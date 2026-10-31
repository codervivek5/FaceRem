"""Password hashing, JWT issue/verify, and the RBAC + tenant-scoping dependencies."""

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import hashlib
import uuid

import bcrypt
import jwt
from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .db import get_db
from .models import AuditLog, RefreshToken, Role, User

settings = get_settings()
bearer_scheme = HTTPBearer(auto_error=False)


class AppError(Exception):
    """Domain error carrying a machine-readable code, rendered by the envelope handler."""

    def __init__(self, code: str, message: str, status_code: int = 400):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt()).decode()


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(plain.encode(), hashed.encode())
    except ValueError:
        return False


def _issue(subject: str, token_type: str, expires: timedelta, **claims) -> str:
    now = datetime.now(timezone.utc)
    payload = {"sub": subject, "type": token_type, "iat": now, "exp": now + expires, **claims}
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def create_access_token(user: User) -> str:
    return _issue(
        user.id,
        "access",
        timedelta(minutes=settings.access_token_minutes),
        org=user.organization_id,
        role=user.role.value,
    )


def create_refresh_token(user: User) -> str:
    # `jti` makes every refresh token unique. Without it two logins in the same second
    # produce byte-identical tokens — the claims only carry second resolution — which
    # collides on the stored hash and, worse, would make one logout kill both sessions.
    return _issue(
        user.id,
        "refresh",
        timedelta(days=settings.refresh_token_days),
        jti=str(uuid.uuid4()),
    )


def decode_token(token: str, expected_type: str) -> dict:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except jwt.ExpiredSignatureError:
        raise AppError("TOKEN_EXPIRED", "Your session has expired. Please sign in again.", 401)
    except jwt.PyJWTError:
        raise AppError("UNAUTHORIZED", "Authentication failed.", 401)
    if payload.get("type") != expected_type:
        raise AppError("UNAUTHORIZED", "Authentication failed.", 401)
    return payload


@dataclass(frozen=True)
class Principal:
    """The authenticated caller. `organization_id` comes from the token, never from the request."""

    user_id: str
    organization_id: str | None
    role: Role
    employee_id: str | None
    full_name: str
    email: str


def get_principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> Principal:
    if credentials is None:
        raise AppError("UNAUTHORIZED", "Authentication required.", 401)

    payload = decode_token(credentials.credentials, "access")
    user = db.get(User, payload["sub"])
    if user is None or not user.is_active:
        raise AppError("UNAUTHORIZED", "Authentication failed.", 401)

    return Principal(
        user_id=user.id,
        organization_id=user.organization_id,
        role=user.role,
        employee_id=user.employee_id,
        full_name=user.full_name,
        email=user.email,
    )


def require_roles(*allowed: Role):
    """
    Dependency factory for role checks.

    SUPER_ADMIN is deliberately not an implicit member of any tenant scope: platform staff must
    not be able to read a customer's employees or biometric data through the ordinary API.
    """

    def dependency(principal: Principal = Depends(get_principal)) -> Principal:
        if principal.role not in allowed:
            raise AppError("FORBIDDEN", "You do not have permission to perform this action.", 403)
        return principal

    return dependency


def tenant_scope(principal: Principal = Depends(get_principal)) -> Principal:
    """Guarantees the caller belongs to exactly one organization before any tenant query runs."""
    if principal.role is Role.SUPER_ADMIN or principal.organization_id is None:
        raise AppError("FORBIDDEN", "This endpoint is scoped to a single organization.", 403)
    return principal


def record_audit(
    db: Session,
    *,
    action: str,
    principal: Principal | None = None,
    organization_id: str | None = None,
    target_type: str = "",
    target_id: str = "",
    request: Request | None = None,
    **meta,
) -> None:
    """Writes an audit row. Callers must pass only non-sensitive values in `meta`."""
    if request is not None and request.client is not None:
        meta.setdefault("client_host", request.client.host)
    db.add(
        AuditLog(
            organization_id=organization_id or (principal.organization_id if principal else None),
            actor_user_id=principal.user_id if principal else None,
            action=action,
            target_type=target_type,
            target_id=target_id,
            meta=meta,
        )
    )


# ---------------------------------------------------------------------------
# Who may hand out which role
# ---------------------------------------------------------------------------

#: What each role is allowed to assign. The point is to stop privilege escalation: an
#: HR admin must not be able to promote anyone — including themselves — to ORG_ADMIN.
#: SUPER_ADMIN appears in no list, so platform access can never be granted through the
#: tenant API.
ASSIGNABLE_ROLES: dict[Role, tuple[Role, ...]] = {
    Role.ORG_ADMIN: (
        Role.ORG_ADMIN,
        Role.HR_ADMIN,
        Role.MANAGER,
        Role.EMPLOYEE,
        Role.ATTENDANCE_DEVICE,
    ),
    Role.HR_ADMIN: (Role.MANAGER, Role.EMPLOYEE, Role.ATTENDANCE_DEVICE),
}


def assert_can_assign(actor: Role, target: Role) -> None:
    if target not in ASSIGNABLE_ROLES.get(actor, ()):
        raise AppError(
            "FORBIDDEN",
            f"Your role cannot assign {target.value}.",
            403,
        )


# ---------------------------------------------------------------------------
# Refresh tokens
# ---------------------------------------------------------------------------

def _fingerprint(token: str) -> str:
    """SHA-256 of the token. Storing the token itself would make the table as good as a
    password file; a hash lets us look a token up without being able to reproduce it."""
    return hashlib.sha256(token.encode()).hexdigest()


def issue_refresh_token(db: Session, user: User) -> str:
    token = create_refresh_token(user)
    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=_fingerprint(token),
            expires_at=datetime.now(timezone.utc) + timedelta(days=settings.refresh_token_days),
        )
    )
    return token


def revoke_all_for_user(db: Session, user_id: str) -> int:
    """Signs the user out everywhere. Used on logout-all and on suspected token theft."""
    now = datetime.now(timezone.utc)
    rows = db.scalars(
        select(RefreshToken).where(
            RefreshToken.user_id == user_id, RefreshToken.revoked_at.is_(None)
        )
    ).all()
    for row in rows:
        row.revoked_at = now
    return len(rows)


def consume_refresh_token(db: Session, token: str) -> User:
    """
    Verify a refresh token and burn it, returning its owner.

    Presenting a token that was already used is the signature of a stolen token being
    replayed, so every session for that user is ended rather than just refusing this one.
    """
    claims = decode_token(token, "refresh")
    stored = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == _fingerprint(token)))

    if stored is None:
        raise AppError("UNAUTHORIZED", "Authentication failed.", 401)

    if stored.revoked_at is not None:
        revoke_all_for_user(db, stored.user_id)
        db.commit()
        raise AppError(
            "TOKEN_REUSED",
            "This session was ended for security. Please sign in again.",
            401,
        )

    if stored.expires_at.replace(tzinfo=timezone.utc) < datetime.now(timezone.utc):
        raise AppError("TOKEN_EXPIRED", "Your session has expired. Please sign in again.", 401)

    user = db.get(User, claims["sub"])
    if user is None or not user.is_active:
        raise AppError("UNAUTHORIZED", "Authentication failed.", 401)

    stored.revoked_at = datetime.now(timezone.utc)
    return user


def revoke_refresh_token(db: Session, token: str) -> None:
    """Best-effort logout: an unknown or already-dead token is not an error to the caller."""
    stored = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == _fingerprint(token)))
    if stored is not None and stored.revoked_at is None:
        stored.revoked_at = datetime.now(timezone.utc)
