###############################################################################
# TranscriptAI (Colloquia) compute infra — FREE-TIER target.
#
# Just the API box: one t2.micro EC2 (free-tier eligible) running FastAPI behind
# nginx. The database stays on Supabase and media on the EXISTING field-repository
# S3 bucket (reused via the field-repo's IAM keys in the backend .env), so this box
# is stateless and holds no S3/IAM resources of its own — keeping it minimal and
# inside the free tier.
#
# A deploy SSH keypair is generated here and written to colloquia-deploy.pem
# (gitignored) so `terraform apply` is self-contained — no manual keypair needed.
#
# Usage:
#   cd infra/terraform
#   terraform init
#   terraform apply -var="ssh_ingress_cidr=0.0.0.0/0"
#
# NEVER commit terraform.tfstate or *.pem (gitignored): state holds the private key.
###############################################################################

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws   = { source = "hashicorp/aws", version = "~> 5.0" }
    tls   = { source = "hashicorp/tls", version = "~> 4.0" }
    local = { source = "hashicorp/local", version = "~> 2.0" }
  }
}

provider "aws" {
  region = var.aws_region
}

############################ self-contained keypair ###########################

resource "tls_private_key" "deploy" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "deploy" {
  key_name   = "${var.project}-deploy"
  public_key = tls_private_key.deploy.public_key_openssh
}

resource "local_sensitive_file" "deploy_pem" {
  content         = tls_private_key.deploy.private_key_pem
  filename        = "${path.module}/colloquia-deploy.pem"
  file_permission = "0600"
}

################################ latest Ubuntu ################################

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

############################### security group ###############################

resource "aws_security_group" "api" {
  name        = "${var.project}-api"
  description = "TranscriptAI API: SSH + HTTP/HTTPS via nginx"

  ingress {
    description = "SSH (key-based; restrict the CIDR for tighter security)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_ingress_cidr]
  }
  ingress {
    description = "HTTP (nginx to uvicorn)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

############################### EC2 (t2.micro) ###############################

resource "aws_instance" "api" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type # t2.micro = free-tier eligible
  key_name               = aws_key_pair.deploy.key_name
  vpc_security_group_ids = [aws_security_group.api.id]
  user_data              = file("${path.module}/user_data.sh")

  root_block_device {
    volume_size = var.root_volume_gb
    volume_type = "gp3"
  }

  tags = {
    Name    = "${var.project}-api"
    Project = var.project
  }
}

resource "aws_eip" "api" {
  instance = aws_instance.api.id
  domain   = "vpc"
  tags     = { Name = "${var.project}-api" }
}

########################### CloudFront (HTTPS + IPv6) #########################
# Fronts the HTTP-only EC2 origin with HTTPS on a *.cloudfront.net domain and a
# native IPv6 (AAAA) record — so the app connects on IPv6-only mobile networks
# (Jio/Airtel) where the IPv4-only origin fails, and clears any mixed-content block.
# CloudFront needs a DOMAIN origin (not a bare IP), so we use the EIP's AWS hostname.

locals {
  origin_domain = "ec2-${replace(aws_eip.api.public_ip, ".", "-")}.${var.aws_region}.compute.amazonaws.com"
}

resource "aws_cloudfront_distribution" "api" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.project} API"
  price_class     = "PriceClass_100" # cheapest: NA + EU edges

  origin {
    domain_name = local.origin_domain
    origin_id   = "ec2-api"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # origin has no TLS cert; CloudFront terminates HTTPS
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = 60
    }
  }

  default_cache_behavior {
    target_origin_id       = "ec2-api"
    viewer_protocol_policy  = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # It's an API: forward everything, cache nothing.
    forwarded_values {
      query_string = true
      headers      = ["*"]
      cookies { forward = "all" }
    }
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = { Name = "${var.project}-api", Project = var.project }
}
