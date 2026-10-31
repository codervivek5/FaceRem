from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_DEV_SECRET = "dev-only-insecure-secret-change-me"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "dev"
    database_url: str = "sqlite:///./faceattend.db"

    jwt_secret: str = DEFAULT_DEV_SECRET
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 30
    refresh_token_days: int = 14

    face_engine: str = "stub"
    # Leave unset to use the active engine's own default: the right value for a
    # 256-dim grayscale descriptor is not the right value for ArcFace.
    face_match_threshold: float | None = None

    liveness_mode: str = "client_attested"
    liveness_threshold: float = 0.7

    # Minutes that must pass after check-in before another scan counts as check-out.
    # Below this, a repeat scan is reported as ALREADY_PRESENT instead of punching out,
    # so someone walking past the gate mid-shift cannot accidentally end their day.
    checkout_after_minutes: int = 240
    shift_start_hour: int = 9
    shift_grace_minutes: int = 10

    seed_demo_data: bool = True

    @property
    def is_production(self) -> bool:
        return self.app_env.lower() in {"prod", "production"}


MIN_SECRET_BYTES = 32


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    if settings.is_production:
        if settings.jwt_secret == DEFAULT_DEV_SECRET:
            raise RuntimeError("JWT_SECRET must be set to a real value outside development.")
        if len(settings.jwt_secret.encode()) < MIN_SECRET_BYTES:
            raise RuntimeError(
                f"JWT_SECRET must be at least {MIN_SECRET_BYTES} bytes for HS256. "
                "Generate one with: python3 -c 'import secrets; print(secrets.token_urlsafe(48))'"
            )
    return settings
