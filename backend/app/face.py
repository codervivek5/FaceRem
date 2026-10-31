"""
Face embedding, liveness, and organization-scoped vector search.

Recognition is a server responsibility. The Android client only detects a face, checks frame
quality and runs on-device liveness; it never decides who someone is.
"""

from __future__ import annotations

import io
from dataclasses import dataclass
from functools import lru_cache

import numpy as np
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .models import Employee, FaceProfile
from .security import AppError

settings = get_settings()


@dataclass(frozen=True)
class Embedding:
    vector: list[float]
    quality: float
    model_name: str
    model_version: str


class StubEngine:
    """
    A 256-dimension normalised grayscale descriptor.

    This is NOT a face recogniser. It matches near-identical images, which is enough to exercise
    enrollment, matching, thresholds and tenant isolation end to end without downloading a model.
    Use FACE_ENGINE=insightface for anything real.
    """

    model_name = "stub-gray16"
    model_version = "0"
    #: Tuned for this descriptor. It separates poorly, so the bar has to sit high.
    default_threshold = 0.62

    def embed(self, image_bytes: bytes) -> Embedding:
        from PIL import Image, ImageOps

        try:
            image = Image.open(io.BytesIO(image_bytes))
            image = ImageOps.exif_transpose(image).convert("L").resize((16, 16))
        except Exception:
            raise AppError("FACE_QUALITY_LOW", "The uploaded image could not be read.", 422)

        pixels = np.asarray(image, dtype=np.float32).flatten()
        centred = pixels - pixels.mean()
        norm = float(np.linalg.norm(centred))
        if norm < 1e-6:
            raise AppError("FACE_QUALITY_LOW", "The image is blank or has no detail.", 422)

        # Contrast stands in for a quality score: a flat frame is a bad frame either way.
        quality = float(min(1.0, pixels.std() / 64.0))
        return Embedding((centred / norm).tolist(), quality, self.model_name, self.model_version)


class InsightFaceEngine:
    """
    Real face recognition: ArcFace embeddings via InsightFace.

    Requires `pip install insightface onnxruntime`. The ~280 MB model downloads to
    ~/.insightface on first use, so the first request after a cold start is slow.
    """

    model_name = "buffalo_l"
    model_version = "1"
    #: ArcFace separates far better, so the usable band is much lower. Reusing the stub's
    #: 0.62 here would reject the same person photographed in different light.
    default_threshold = 0.45

    #: Fraction of the image added as a border when a first pass finds nothing. The detector
    #: needs context around a face, so a tightly cropped ID photo reads as "no face" until
    #: it is padded. Upscaling does not help; only the margin does.
    _PAD_RATIO = 0.5

    def __init__(self) -> None:
        from insightface.app import FaceAnalysis

        self._app = FaceAnalysis(name=self.model_name)
        self._app.prepare(ctx_id=-1, det_size=(640, 640))

    def embed(self, image_bytes: bytes) -> Embedding:
        from PIL import Image, ImageOps, UnidentifiedImageError

        try:
            image = Image.open(io.BytesIO(image_bytes))
            image = ImageOps.exif_transpose(image).convert("RGB")
        except (UnidentifiedImageError, OSError):
            raise AppError("FACE_QUALITY_LOW", "The uploaded image could not be read.", 422)

        faces = self._detect(image)
        if not faces:
            # Employee photos are often supplied as tight head crops. Retry with a margin
            # rather than rejecting a perfectly usable picture.
            border = int(min(image.size) * self._PAD_RATIO)
            faces = self._detect(ImageOps.expand(image, border=border, fill=(128, 128, 128)))

        if not faces:
            raise AppError("FACE_NOT_FOUND", "No face was detected in the image.", 422)
        if len(faces) > 1:
            raise AppError("MULTIPLE_FACES", "More than one face was detected.", 422)

        face = faces[0]
        vector = np.asarray(face.normed_embedding, dtype=np.float32)
        # normed_embedding is already unit length; find_best_match relies on that to treat
        # a dot product as cosine similarity.
        quality = float(min(1.0, float(face.det_score)))
        return Embedding(vector.tolist(), quality, self.model_name, self.model_version)

    def _detect(self, image) -> list:
        rgb = np.asarray(image)
        # InsightFace wants BGR, and a reversed slice is a non-contiguous view that the
        # ONNX runtime cannot consume directly.
        return self._app.get(np.ascontiguousarray(rgb[:, :, ::-1]))


@lru_cache
def get_engine():
    if settings.face_engine == "insightface":
        return InsightFaceEngine()
    return StubEngine()


@dataclass(frozen=True)
class LivenessResult:
    passed: bool
    score: float
    provider: str


def check_liveness(client_score: float | None) -> LivenessResult:
    """
    Liveness runs on the device (KBY-AI FaceLivenessDetection-Android returns a per-face score).
    The server enforces the threshold and records the score.

    A client-reported score is evidence, not proof: a modified client can send any number. Before
    production this must be replaced by a server-side check, or by an attestation the server can
    verify (Play Integrity plus an SDK-signed result). The mode is explicit so that decision is
    visible rather than assumed.
    """
    mode = settings.liveness_mode
    if mode == "disabled":
        return LivenessResult(True, 0.0, "disabled")

    if client_score is None:
        raise AppError("LIVENESS_FAILED", "Liveness result missing from the request.", 422)
    if client_score < settings.liveness_threshold:
        raise AppError("LIVENESS_FAILED", "Liveness check failed. Please try again.", 422)
    return LivenessResult(True, float(client_score), "client_attested")


@dataclass(frozen=True)
class Match:
    employee: Employee
    confidence: float


def find_best_match(db: Session, organization_id: str, vector: list[float]) -> Match | None:
    """
    Organization-scoped nearest-neighbour search.

    The organization filter is applied BEFORE similarity, never after: a face must never be
    compared against another tenant's profiles. With pgvector this becomes
    `WHERE organization_id = :org AND revoked_at IS NULL ORDER BY embedding <=> :q LIMIT 1`.
    """
    engine = get_engine()
    rows = db.execute(
        select(FaceProfile, Employee)
        .join(Employee, Employee.id == FaceProfile.employee_id)
        .where(
            FaceProfile.organization_id == organization_id,
            FaceProfile.revoked_at.is_(None),
            Employee.is_active.is_(True),
            # Embeddings from a different model are not comparable — different dimensions
            # and a different vector space. Filtering in SQL keeps a mixed table from
            # producing a ragged matrix, which would raise rather than simply not match.
            # Switching engines therefore requires re-enrollment; see the README.
            FaceProfile.model_name == engine.model_name,
            FaceProfile.model_version == engine.model_version,
        )
    ).all()
    if not rows:
        return None

    query = np.asarray(vector, dtype=np.float32)
    matrix = np.asarray([row[0].embedding for row in rows], dtype=np.float32)
    if matrix.shape[1] != query.shape[0]:  # defensive: a stored vector of the wrong width
        return None

    scores = matrix @ query  # Both sides are L2-normalised, so this is cosine similarity.
    best = int(np.argmax(scores))
    confidence = float(scores[best])

    # Below the threshold the answer is "unknown". Never return the nearest candidate.
    if confidence < match_threshold():
        return None
    return Match(employee=rows[best][1], confidence=confidence)


def match_threshold() -> float:
    """The configured threshold, or the active engine's own default when none is set."""
    configured = settings.face_match_threshold
    return configured if configured is not None else get_engine().default_threshold
