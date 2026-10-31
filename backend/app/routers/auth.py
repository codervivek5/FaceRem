"""Sign in, token refresh, and who the caller is."""

import re
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import (
    PLAN_SEATS,
    Organization,
    Role,
    Subscription,
    SubscriptionStatus,
    User,
)
from ..schemas import (
    ChangePasswordRequest,
    LoginRequest,
    MeResponse,
    RefreshRequest,
    SignupRequest,
    SignupResponse,
    TokenPair,
)
from ..security import (
    AppError,
    Principal,
    consume_refresh_token,
    create_access_token,
    get_principal,
    hash_password,
    issue_refresh_token,
    record_audit,
    revoke_all_for_user,
    revoke_refresh_token,
    verify_password,
)

#: How long a self-serve trial lasts before it needs a plan.
TRIAL_DAYS = 14

router = APIRouter()


@router.post("/auth/login", response_model=TokenPair)
def login(payload: LoginRequest, request: Request, db: Session = Depends(get_db)) -> TokenPair:
    query = select(User).where(User.email == payload.email.strip().lower())
    if payload.organization_slug:
        organization = db.scalar(
            select(Organization).where(Organization.slug == payload.organization_slug)
        )
        if organization is None:
            # Same error as a bad password, so slugs cannot be probed.
            raise AppError("INVALID_CREDENTIALS", "Email or password is incorrect.", 401)
        query = query.where(User.organization_id == organization.id)

    users = db.scalars(query).all()
    user = next((candidate for candidate in users if candidate.is_active), None)

    if user is None or not verify_password(payload.password, user.password_hash):
        record_audit(
            db,
            action="auth.login_failed",
            request=request,
            email_domain=payload.email.split("@")[-1] if "@" in payload.email else "",
        )
        db.commit()
        raise AppError("INVALID_CREDENTIALS", "Email or password is incorrect.", 401)

    if user.organization_id is not None:
        organization = db.get(Organization, user.organization_id)
        if organization is None or not organization.is_active:
            raise AppError("ORGANIZATION_SUSPENDED", "This organization is not active.", 403)

        # PAST_DUE deliberately still works: failed payment starts dunning, not a lockout.
        subscription = db.scalar(
            select(Subscription).where(Subscription.organization_id == organization.id)
        )
        if subscription is not None and subscription.status is SubscriptionStatus.CANCELLED:
            raise AppError(
                "SUBSCRIPTION_CANCELLED",
                "This organization's subscription has ended. Contact your administrator.",
                403,
            )

    record_audit(
        db,
        action="auth.login",
        organization_id=user.organization_id,
        target_type="user",
        target_id=user.id,
        request=request,
        role=user.role.value,
    )
    db.commit()
    tokens = TokenPair(
        access_token=create_access_token(user),
        refresh_token=issue_refresh_token(db, user),
    )
    db.commit()
    return tokens


@router.post("/auth/refresh", response_model=TokenPair)
def refresh(payload: RefreshRequest, db: Session = Depends(get_db)) -> TokenPair:
    """
    Exchange a refresh token for a new pair, burning the old one.

    Rotation is what makes logout mean anything: the token just used is dead, so a copy
    taken earlier is detectably stale rather than valid until it expires.
    """
    user = consume_refresh_token(db, payload.refresh_token)
    tokens = TokenPair(
        access_token=create_access_token(user),
        refresh_token=issue_refresh_token(db, user),
    )
    db.commit()
    return tokens


@router.get("/auth/me", response_model=MeResponse)
def me(principal: Principal = Depends(get_principal), db: Session = Depends(get_db)) -> MeResponse:
    organization = (
        db.get(Organization, principal.organization_id) if principal.organization_id else None
    )
    return MeResponse(
        id=principal.user_id,
        full_name=principal.full_name,
        email=principal.email,
        role=principal.role,
        organization_id=principal.organization_id,
        organization_name=organization.name if organization else None,
        employee_id=principal.employee_id,
    )


@router.post("/auth/signup", response_model=SignupResponse, status_code=201)
def signup(payload: SignupRequest, request: Request, db: Session = Depends(get_db)) -> SignupResponse:
    """
    Create an organization and its first administrator.

    This is the only endpoint that makes a tenant, and the only place ORG_ADMIN is granted
    without an existing admin approving it — which is exactly what self-serve signup means.
    """
    slug = (payload.organization_slug or _slugify(payload.organization_name)).lower()
    if not slug:
        raise AppError("VALIDATION_ERROR", "Could not derive an organization code.", 422)

    if db.scalar(select(Organization).where(Organization.slug == slug)) is not None:
        raise AppError("CONFLICT", "That organization code is already taken.", 409)

    email = str(payload.email).strip().lower()
    organization = Organization(name=payload.organization_name.strip(), slug=slug)
    db.add(organization)
    db.flush()

    # Every tenant starts metered. Without this a new signup would be unlimited.
    db.add(
        Subscription(
            organization_id=organization.id,
            plan="TRIAL",
            seat_limit=PLAN_SEATS["TRIAL"],
            status=SubscriptionStatus.TRIALING,
            current_period_end=datetime.now(timezone.utc) + timedelta(days=TRIAL_DAYS),
        )
    )

    user = User(
        organization_id=organization.id,
        email=email,
        password_hash=hash_password(payload.password),
        full_name=payload.full_name.strip(),
        role=Role.ORG_ADMIN,
    )
    db.add(user)
    db.flush()

    record_audit(
        db,
        action="auth.signup",
        organization_id=organization.id,
        target_type="user",
        target_id=user.id,
        request=request,
        organization_slug=slug,
    )
    response = SignupResponse(
        organization_id=organization.id,
        organization_slug=slug,
        user_id=user.id,
        access_token=create_access_token(user),
        refresh_token=issue_refresh_token(db, user),
    )
    db.commit()
    return response


def _slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:64]


@router.post("/auth/logout")
def logout(
    payload: RefreshRequest,
    request: Request,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
) -> dict:
    """Ends this session. The access token still works until it expires, which is why it
    is short-lived; the refresh token is what actually keeps someone signed in."""
    revoke_refresh_token(db, payload.refresh_token)
    record_audit(db, action="auth.logout", principal=principal, request=request)
    db.commit()
    return {"signed_out": True}


@router.post("/auth/change-password")
def change_password(
    payload: ChangePasswordRequest,
    request: Request,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
) -> dict:
    user = db.get(User, principal.user_id)
    if user is None or not verify_password(payload.current_password, user.password_hash):
        # Deliberately vague: this must not confirm anything to someone holding a stolen token.
        raise AppError("INVALID_CREDENTIALS", "Current password is incorrect.", 401)

    user.password_hash = hash_password(payload.new_password)
    # A password change should end other sessions — that is usually why it is being changed.
    revoked = revoke_all_for_user(db, user.id)
    record_audit(
        db,
        action="auth.password_changed",
        principal=principal,
        target_type="user",
        target_id=user.id,
        request=request,
        sessions_ended=revoked,
    )
    db.commit()
    return {"password_changed": True, "other_sessions_ended": revoked}
