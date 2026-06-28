#!/usr/bin/env bash
# First-boot provisioning for the TranscriptAI (Colloquia) API box (Ubuntu 24.04).
# Installs python3.12 + ffmpeg (Whisper audio chunking) + nginx (reverse proxy so port 8010 is never
# exposed directly), adds swap so pip/prisma installs don't OOM on a 1 GiB t2.micro, and lays down the
# nginx site + systemd unit. Application code + .env are deployed by the GitHub Actions workflow.
set -euxo pipefail

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y python3.12-venv python3-pip git ffmpeg nginx

cat > /etc/nginx/sites-available/colloquia <<'NGINX'
server {
    listen 80 default_server;
    server_name _;
    client_max_body_size 200M;

    location / {
        proxy_pass http://127.0.0.1:8010;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        # WebSocket upgrade for live transcription.
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGINX
ln -sf /etc/nginx/sites-available/colloquia /etc/nginx/sites-enabled/colloquia
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl restart nginx

# --- systemd unit for the API (uvicorn) --------------------------------------
# IMPORTANT: a SINGLE uvicorn process (NOT --workers 2). With >1 worker uvicorn runs a multiprocess
# supervisor that health-pings each worker over a pipe and SIGKILLs any worker that fails to pong
# within timeout_worker_healthcheck. On this small, CPU-credit-throttled box a heavy transcription
# chunk (asyncio.to_thread) starved that pong thread, so the supervisor SIGKILLed the worker mid-job.
# SIGKILL skips the shutdown hook, so the worker's Prisma query-engine subprocess was orphaned — one
# orphan per kill cycle until the orphans exhausted the Supabase pooler and EVERY DB call (login
# included) returned HTTP 500 while /health (no DB) stayed 200. One process = no supervisor = no
# SIGKILL loop. The media queue runs in its OWN service (colloquia-queue, below), so heavy AI/ffmpeg
# work is never in the request path. MEDIA_QUEUE_WORKER_ENABLED=false disables the in-process queue.
cat > /etc/systemd/system/colloquia.service <<'UNIT'
[Unit]
Description=TranscriptAI (Colloquia) API
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/app/backend
EnvironmentFile=/home/ubuntu/app/backend/.env
# Applied AFTER EnvironmentFile so it always wins: the web process must never run the queue.
Environment=MEDIA_QUEUE_WORKER_ENABLED=false
ExecStart=/home/ubuntu/app/backend/.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8010 --workers 1
Restart=always
RestartSec=10
# Reap the whole control group on stop/restart so a Prisma query-engine is never left orphaned.
KillMode=control-group
TimeoutStopSec=20

[Install]
WantedBy=multi-user.target
UNIT

# --- systemd unit for the media-processing queue worker ----------------------
# Runs the transcription/analysis queue in its OWN process (see app/worker.py). Separate from uvicorn
# on purpose: no multiprocess supervisor can SIGKILL it mid-job, and its heavy work never competes with
# request serving. KillMode=control-group reaps its query-engine on restart.
cat > /etc/systemd/system/colloquia-queue.service <<'UNIT'
[Unit]
Description=TranscriptAI (Colloquia) media queue worker
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/app/backend
EnvironmentFile=/home/ubuntu/app/backend/.env
ExecStart=/home/ubuntu/app/backend/.venv/bin/python -m app.worker
Restart=always
RestartSec=10
KillMode=control-group
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable colloquia || true
systemctl enable colloquia-queue || true
mkdir -p /home/ubuntu/app
chown -R ubuntu:ubuntu /home/ubuntu/app
