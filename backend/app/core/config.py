from functools import lru_cache

from pydantic import AnyHttpUrl, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = Field(alias="DATABASE_URL")
    # Route runtime queries through the Supabase transaction-mode pooler (:6543, pgbouncer=true) so a
    # couple of uvicorn workers don't exhaust the session pooler's 15 server connections. See
    # core/db.py for the full rationale (ported from the field-repository backend).
    database_use_transaction_pooler: bool = Field(
        default=True, alias="DATABASE_USE_TRANSACTION_POOLER"
    )
    database_connection_limit: int = Field(default=10, alias="DATABASE_CONNECTION_LIMIT")
    database_pool_timeout: int | None = Field(default=None, alias="DATABASE_POOL_TIMEOUT")

    jwt_secret: str = Field(alias="JWT_SECRET")
    jwt_algorithm: str = "HS256"
    jwt_expires_minutes: int = Field(default=60 * 24 * 7, alias="JWT_EXPIRES_MINUTES")

    aws_access_key_id: str = Field(alias="AWS_ACCESS_KEY_ID")
    aws_secret_access_key: str = Field(alias="AWS_SECRET_ACCESS_KEY")
    aws_region: str = Field(default="us-east-1", alias="AWS_REGION")
    aws_s3_bucket: str = Field(alias="AWS_S3_BUCKET")
    aws_s3_endpoint: str | None = Field(default=None, alias="AWS_S3_ENDPOINT")
    aws_s3_public_base_url: str | None = Field(default=None, alias="AWS_S3_PUBLIC_BASE_URL")

    app_url: AnyHttpUrl | str = Field(default="http://localhost:3000", alias="APP_URL")
    backend_cors_origins: str = Field(default="http://localhost:3000", alias="BACKEND_CORS_ORIGINS")

    google_client_id: str | None = Field(default=None, alias="GOOGLE_CLIENT_ID")
    google_android_client_id: str | None = Field(default=None, alias="GOOGLE_ANDROID_CLIENT_ID")

    # Microsoft (Azure AD) OAuth — authorization-code flow. tenant "common" allows both personal and
    # work/school accounts. Absent client id/secret -> the /login Microsoft path returns a clean 400.
    microsoft_client_id: str | None = Field(default=None, alias="MICROSOFT_CLIENT_ID")
    microsoft_client_secret: str | None = Field(default=None, alias="MICROSOFT_CLIENT_SECRET")
    microsoft_tenant: str = Field(default="common", alias="MICROSOFT_TENANT")

    # Yahoo OAuth 2.0 / OpenID Connect — authorization-code flow (confidential client).
    yahoo_client_id: str | None = Field(default=None, alias="YAHOO_CLIENT_ID")
    yahoo_client_secret: str | None = Field(default=None, alias="YAHOO_CLIENT_SECRET")

    master_admin_email: str = Field(default="", alias="MASTER_ADMIN_EMAIL")
    master_admin_name: str = Field(default="Admin", alias="MASTER_ADMIN_NAME")

    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    openai_transcription_model: str = Field(default="whisper-1", alias="OPENAI_TRANSCRIPTION_MODEL")
    openai_chat_model: str = Field(default="gpt-4o-mini", alias="OPENAI_CHAT_MODEL")

    media_queue_worker_enabled: bool = Field(default=True, alias="MEDIA_QUEUE_WORKER_ENABLED")
    media_queue_interval_seconds: float = Field(default=5.0, alias="MEDIA_QUEUE_INTERVAL_SECONDS")
    media_queue_batch_size: int = Field(default=3, alias="MEDIA_QUEUE_BATCH_SIZE")
    media_queue_job_max_attempts: int = Field(default=3, alias="MEDIA_QUEUE_JOB_MAX_ATTEMPTS")

    # --- Phase 2 -----------------------------------------------------------------------------------
    # Lite mode: when true, skip compute-heavy work (embeddings generation, live streaming STT, heavy
    # diarization) so the app runs comfortably on a free-tier box. Feature ENDPOINTS still exist; they
    # fall back to lighter behaviour (e.g. semantic search -> keyword search). Toggleable at runtime
    # via the AppSetting row too; this env sets the default/ceiling.
    lite_mode: bool = Field(default=True, alias="LITE_MODE")
    embedding_model: str = Field(default="text-embedding-3-small", alias="OPENAI_EMBEDDING_MODEL")
    # Optional diarization provider key (e.g. Deepgram). Absent -> chat-model labelling is used.
    deepgram_api_key: str | None = Field(default=None, alias="DEEPGRAM_API_KEY")
    # FCM HTTP v1 (the legacy server key is decommissioned). Point this at a Firebase service-account
    # JSON file; the backend mints a short-lived OAuth token from it per send. Absent -> tokens are
    # stored but no push is sent (graceful no-op).
    fcm_service_account_file: str | None = Field(default=None, alias="FCM_SERVICE_ACCOUNT_FILE")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        populate_by_name=True,
        extra="ignore",
    )

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.backend_cors_origins.split(",") if origin.strip()]

    @property
    def google_client_ids(self) -> list[str]:
        return [v for v in [self.google_client_id, self.google_android_client_id] if v]


@lru_cache
def get_settings() -> Settings:
    return Settings()
