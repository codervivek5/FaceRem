"""
Tests for the real face engine.

Kept out of test_api.py because they load a ~280 MB model: the API suite runs on the stub
and stays fast, while these prove the thing the product actually sells.
"""

import io
import os
import sys
from pathlib import Path

import numpy as np
import pytest
from PIL import Image, ImageEnhance

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ.setdefault("JWT_SECRET", "test-secret-that-is-long-enough-for-hs256-abcdef")

insightface = pytest.importorskip("insightface", reason="real engine not installed")

from app.face import InsightFaceEngine, StubEngine  # noqa: E402
from app.security import AppError  # noqa: E402

SAMPLES = Path(insightface.__file__).parent / "data" / "images"


@pytest.fixture(scope="module")
def engine():
    return InsightFaceEngine()


def _jpeg(image: Image.Image, quality: int = 70) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=quality)
    return buffer.getvalue()


@pytest.fixture(scope="module")
def faces(engine):
    """Two different people cropped out of the bundled group photo."""
    scene = Image.open(SAMPLES / "t1.jpg").convert("RGB")
    detected = sorted(
        engine._detect(scene), key=lambda f: f.bbox[0]
    )
    assert len(detected) >= 4, "the sample photo should contain several faces"

    def crop(face, pad=0.6):
        x1, y1, x2, y2 = face.bbox
        w, h = x2 - x1, y2 - y1
        return scene.crop(
            (
                max(0, int(x1 - w * pad)),
                max(0, int(y1 - h * pad)),
                min(scene.width, int(x2 + w * pad)),
                min(scene.height, int(y2 + h * pad)),
            )
        )

    return crop(detected[0]), crop(detected[3])


def _similarity(engine, a: bytes, b: bytes) -> float:
    va = np.asarray(engine.embed(a).vector)
    vb = np.asarray(engine.embed(b).vector)
    return float(np.dot(va, vb))


def test_embeddings_are_unit_length_arcface_vectors(engine, faces):
    embedding = engine.embed(_jpeg(faces[0]))
    assert len(embedding.vector) == 512
    assert np.isclose(np.linalg.norm(embedding.vector), 1.0, atol=1e-3), (
        "find_best_match treats a dot product as cosine similarity, which only holds "
        "while embeddings are L2-normalised"
    )


def test_the_same_person_matches_across_a_different_photo(engine, faces):
    """Lighting, angle, distance and re-compression all change — the person does not."""
    original, _ = faces
    other_day = ImageEnhance.Brightness(original).enhance(0.65)
    other_day = other_day.rotate(9, expand=True, fillcolor=(128, 128, 128))
    other_day = other_day.resize(
        (int(other_day.width * 0.55), int(other_day.height * 0.55)), Image.LANCZOS
    )

    score = _similarity(engine, _jpeg(original), _jpeg(other_day, quality=55))
    assert score > engine.default_threshold, f"same person scored only {score:.3f}"


def test_two_different_people_do_not_match(engine, faces):
    a, b = faces
    score = _similarity(engine, _jpeg(a), _jpeg(b))
    assert score < engine.default_threshold, f"different people scored {score:.3f}"


def test_separation_is_wide_enough_to_be_usable(engine, faces):
    """A threshold is only safe if there is real daylight between the two populations."""
    original, other = faces
    dimmed = ImageEnhance.Brightness(original).enhance(0.65)

    same = _similarity(engine, _jpeg(original), _jpeg(dimmed, quality=55))
    different = _similarity(engine, _jpeg(original), _jpeg(other))
    assert same - different > 0.3, f"only {same - different:.3f} between same and different"


def test_a_tightly_cropped_id_photo_still_enrols(engine):
    """Employee photos often arrive as bare head crops; the detector needs padding to see
    a face at all, so the engine retries rather than rejecting a usable picture."""
    embedding = engine.embed((SAMPLES / "Tom_Hanks_54745.png").read_bytes())
    assert len(embedding.vector) == 512


def test_a_picture_with_no_face_is_rejected(engine):
    noise = Image.fromarray(
        np.random.default_rng(7).integers(0, 255, (480, 480, 3), dtype=np.uint8)
    )
    with pytest.raises(AppError) as caught:
        engine.embed(_jpeg(noise))
    assert caught.value.code == "FACE_NOT_FOUND"


def test_a_group_photo_is_rejected_rather_than_guessed(engine):
    """Enrolling from a photo with several faces would silently bind the wrong person."""
    with pytest.raises(AppError) as caught:
        engine.embed((SAMPLES / "t1.jpg").read_bytes())
    assert caught.value.code == "MULTIPLE_FACES"


def test_corrupt_bytes_are_a_clean_error(engine):
    with pytest.raises(AppError) as caught:
        engine.embed(b"this is not an image")
    assert caught.value.code == "FACE_QUALITY_LOW"


def test_arcface_separates_people_far_better_than_the_stub(engine, faces):
    """
    Guards the reason the real engine exists.

    Both engines are given identical inputs; what matters is the gap between "same person"
    and "different person", because that gap is the only room a threshold has to live in.
    """
    original, other = faces
    other_day = ImageEnhance.Brightness(original).enhance(0.65)
    other_day = other_day.rotate(9, expand=True, fillcolor=(128, 128, 128))
    other_day = other_day.resize(
        (int(other_day.width * 0.55), int(other_day.height * 0.55)), Image.LANCZOS
    )
    same_bytes, other_bytes = _jpeg(other_day, quality=55), _jpeg(other)
    reference = _jpeg(original)

    def separation(candidate) -> float:
        return _similarity(candidate, reference, same_bytes) - _similarity(
            candidate, reference, other_bytes
        )

    assert separation(engine) > separation(StubEngine()) + 0.3
