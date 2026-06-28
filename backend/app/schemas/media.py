from pydantic import BaseModel


class PresignRequest(BaseModel):
    filename: str
    mimeType: str
    sizeBytes: int | None = None


class PresignResponse(BaseModel):
    uploadUrl: str
    method: str
    objectKey: str
    bucket: str
    headers: dict[str, str]
    publicUrl: str | None = None


class MultipartCreateRequest(BaseModel):
    filename: str
    mimeType: str
    sizeBytes: int


class MultipartCreateResponse(BaseModel):
    objectKey: str
    uploadId: str
    bucket: str
    partSize: int
    partCount: int
    publicUrl: str | None = None


class MultipartPresignPartsRequest(BaseModel):
    objectKey: str
    uploadId: str
    partNumbers: list[int]


class MultipartPresignPartsResponse(BaseModel):
    urls: dict[str, str]


class MultipartPart(BaseModel):
    partNumber: int
    etag: str


class MultipartCompleteRequest(BaseModel):
    objectKey: str
    uploadId: str
    parts: list[MultipartPart]


class MultipartCompleteResponse(BaseModel):
    objectKey: str
    bucket: str
    publicUrl: str | None = None


class MultipartAbortRequest(BaseModel):
    objectKey: str
    uploadId: str


class CompleteUploadRequest(BaseModel):
    objectKey: str
    bucket: str | None = None
    url: str | None = None
    mimeType: str | None = None
    originalFilename: str | None = None
    sizeBytes: int | None = None
    durationSec: int | None = None
    title: str | None = None
    folderId: str | None = None
    recordedAt: str | None = None
    # When True (default for audio), enqueue transcription. Lets a client upload without processing.
    transcribe: bool = True
