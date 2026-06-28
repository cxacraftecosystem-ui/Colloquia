from pathlib import PurePath
from typing import Any
from uuid import uuid4

import boto3
from botocore.client import Config

from app.core.config import get_settings


def _client():
    settings = get_settings()
    # Presign against the regional (dual-stack) endpoint for real AWS so the signed Host matches the
    # request Host (avoids 403 SignatureDoesNotMatch from the global-endpoint 307 redirect). A custom
    # endpoint (MinIO) is honoured as-is with path-style addressing. Ported from the field-repo.
    endpoint = settings.aws_s3_endpoint
    s3_config: dict = {}
    if not endpoint and settings.aws_region:
        endpoint = f"https://s3.dualstack.{settings.aws_region}.amazonaws.com"
        s3_config = {"addressing_style": "virtual"}
    return boto3.client(
        "s3",
        region_name=settings.aws_region,
        endpoint_url=endpoint,
        aws_access_key_id=settings.aws_access_key_id,
        aws_secret_access_key=settings.aws_secret_access_key,
        config=Config(signature_version="s3v4", s3=s3_config),
    )


def safe_filename(filename: str) -> str:
    basename = PurePath(filename).name.strip().replace("\\", "-").replace("/", "-")
    cleaned = "".join(ch if ch.isalnum() or ch in {".", "-", "_"} else "-" for ch in basename)
    return cleaned or "upload.bin"


def make_object_key(user_id: str, filename: str) -> str:
    # Same key scheme as the field-repo (media/<user>/<uuid>/<name>); both apps share the bucket
    # without collisions because every key embeds the uploader id + a per-upload uuid.
    return f"media/{user_id}/{uuid4().hex}/{safe_filename(filename)}"


def _promote_dualstack(url: str, region: str | None) -> str:
    if not region:
        return url
    plain = f".s3.{region}.amazonaws.com"
    dual = f".s3.dualstack.{region}.amazonaws.com"
    if dual in url or plain not in url:
        return url
    return url.replace(plain, dual)


def public_url_for_key(object_key: str) -> str | None:
    settings = get_settings()
    if settings.aws_s3_public_base_url:
        base = f"{settings.aws_s3_public_base_url.rstrip('/')}/{object_key}"
    elif settings.aws_s3_endpoint:
        return f"{settings.aws_s3_endpoint.rstrip('/')}/{settings.aws_s3_bucket}/{object_key}"
    else:
        return None
    if not settings.aws_s3_endpoint:
        base = _promote_dualstack(base, settings.aws_region)
    return base


def presign_put_url(object_key: str, mime_type: str) -> str:
    settings = get_settings()
    return _client().generate_presigned_url(
        ClientMethod="put_object",
        Params={"Bucket": settings.aws_s3_bucket, "Key": object_key, "ContentType": mime_type},
        ExpiresIn=900,
        HttpMethod="PUT",
    )


def presign_get_url(object_key: str, expires: int = 3600) -> str:
    """A short-lived presigned GET URL — works even when the bucket is private (it is). Used for OTA
    APK downloads and any authenticated client-side fetch of an object by key."""
    settings = get_settings()
    return _client().generate_presigned_url(
        ClientMethod="get_object",
        Params={"Bucket": settings.aws_s3_bucket, "Key": object_key},
        ExpiresIn=expires,
        HttpMethod="GET",
    )


def create_multipart_upload(object_key: str, mime_type: str) -> str:
    response = _client().create_multipart_upload(
        Bucket=get_settings().aws_s3_bucket,
        Key=object_key,
        ContentType=mime_type,
        ServerSideEncryption="AES256",
    )
    return str(response["UploadId"])


def presign_upload_part(object_key: str, upload_id: str, part_number: int) -> str:
    return _client().generate_presigned_url(
        ClientMethod="upload_part",
        Params={
            "Bucket": get_settings().aws_s3_bucket,
            "Key": object_key,
            "UploadId": upload_id,
            "PartNumber": part_number,
        },
        ExpiresIn=3600,
        HttpMethod="PUT",
    )


def complete_multipart_upload(object_key: str, upload_id: str, parts: list[dict[str, Any]]) -> None:
    _client().complete_multipart_upload(
        Bucket=get_settings().aws_s3_bucket,
        Key=object_key,
        UploadId=upload_id,
        MultipartUpload={"Parts": parts},
    )


def abort_multipart_upload(object_key: str, upload_id: str) -> None:
    _client().abort_multipart_upload(
        Bucket=get_settings().aws_s3_bucket, Key=object_key, UploadId=upload_id
    )


def get_object_bytes(object_key: str) -> bytes:
    settings = get_settings()
    response = _client().get_object(Bucket=settings.aws_s3_bucket, Key=object_key)
    try:
        return response["Body"].read()
    finally:
        response["Body"].close()


def delete_object(object_key: str) -> None:
    settings = get_settings()
    _client().delete_object(Bucket=settings.aws_s3_bucket, Key=object_key)
