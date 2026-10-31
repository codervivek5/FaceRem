"""
Tenant-owned tables all carry `organization_id`. Every query in `api.py` filters on it; a
client-supplied organization id is never trusted.
"""

import enum
import uuid
from datetime import date, datetime, timezone

from sqlalchemy import (
    Boolean,
    Date,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    Integer,
    JSON,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _uuid() -> str:
    return str(uuid.uuid4())


def _now() -> datetime:
    return datetime.now(timezone.utc)


class Role(str, enum.Enum):
    SUPER_ADMIN = "SUPER_ADMIN"
    ORG_ADMIN = "ORG_ADMIN"
    HR_ADMIN = "HR_ADMIN"
    MANAGER = "MANAGER"
    EMPLOYEE = "EMPLOYEE"
    ATTENDANCE_DEVICE = "ATTENDANCE_DEVICE"


class AttendanceStatus(str, enum.Enum):
    PRESENT = "PRESENT"
    LATE = "LATE"
    ABSENT = "ABSENT"
    ON_LEAVE = "ON_LEAVE"


class Organization(Base):
    __tablename__ = "organizations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    name: Mapped[str] = mapped_column(String(160), nullable=False)
    slug: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    plan: Mapped[str] = mapped_column(String(32), default="TRIAL")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class User(Base):
    """A login. `employee_id` links a self-service EMPLOYEE login to its employee record."""

    __tablename__ = "users"
    __table_args__ = (UniqueConstraint("organization_id", "email", name="uq_user_org_email"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=True
    )
    email: Mapped[str] = mapped_column(String(190), nullable=False, index=True)
    password_hash: Mapped[str] = mapped_column(String(128), nullable=False)
    full_name: Mapped[str] = mapped_column(String(160), nullable=False)
    role: Mapped[Role] = mapped_column(Enum(Role), nullable=False)
    employee_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("employees.id"), nullable=True
    )
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class SubscriptionStatus(str, enum.Enum):
    TRIALING = "TRIALING"
    ACTIVE = "ACTIVE"
    #: Payment failed but access continues — dunning, not a lockout.
    PAST_DUE = "PAST_DUE"
    CANCELLED = "CANCELLED"


#: Seats each plan includes. Seats are counted in active employees, because that is what an
#: attendance product actually meters — not logins.
PLAN_SEATS: dict[str, int] = {
    "TRIAL": 25,
    "STARTER": 100,
    "GROWTH": 500,
    "ENTERPRISE": 100_000,
}


class Subscription(Base):
    """
    What a tenant is entitled to.

    Without this the `plan` string was decorative: nothing stopped a trial organization
    adding ten thousand employees.
    """

    __tablename__ = "subscriptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    #: One subscription per organization, so the FK is unique rather than merely indexed.
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, unique=True
    )
    plan: Mapped[str] = mapped_column(String(32), nullable=False, default="TRIAL")
    seat_limit: Mapped[int] = mapped_column(Integer, nullable=False, default=25)
    status: Mapped[SubscriptionStatus] = mapped_column(
        Enum(SubscriptionStatus), nullable=False, default=SubscriptionStatus.TRIALING
    )
    #: When the current billing (or trial) period ends. Null means open-ended.
    current_period_end: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class RefreshToken(Base):
    """
    One issued refresh token.

    Only a hash is stored: a leaked database still cannot be used to mint access tokens.
    Rows are kept after revocation so a replayed token can be recognised as theft rather
    than treated as merely invalid.
    """

    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("users.id"), nullable=False, index=True
    )
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class Department(Base):
    """A tenant-owned lookup entity. Replaces the old free-text `Employee.department` string."""

    __tablename__ = "departments"
    __table_args__ = (UniqueConstraint("organization_id", "name", name="uq_department_org_name"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(96), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class Branch(Base):
    __tablename__ = "branches"
    __table_args__ = (UniqueConstraint("organization_id", "name", name="uq_branch_org_name"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(96), nullable=False)
    city: Mapped[str] = mapped_column(String(96), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class Shift(Base):
    """`start_time`/`end_time` are stored as `HH:MM` strings; a PoC has no per-org timezone yet."""

    __tablename__ = "shifts"
    __table_args__ = (UniqueConstraint("organization_id", "name", name="uq_shift_org_name"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(96), nullable=False)
    start_time: Mapped[str] = mapped_column(String(5), default="09:00")
    end_time: Mapped[str] = mapped_column(String(5), default="17:00")
    grace_minutes: Mapped[int] = mapped_column(Integer, default=10)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class Employee(Base):
    __tablename__ = "employees"
    __table_args__ = (
        UniqueConstraint("organization_id", "employee_code", name="uq_employee_org_code"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    employee_code: Mapped[str] = mapped_column(String(48), nullable=False)
    full_name: Mapped[str] = mapped_column(String(160), nullable=False)
    email: Mapped[str] = mapped_column(String(190), nullable=False)
    phone: Mapped[str] = mapped_column(String(32), default="")
    department_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("departments.id"), nullable=True, index=True
    )
    branch_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("branches.id"), nullable=True, index=True
    )
    shift_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("shifts.id"), nullable=True, index=True
    )
    job_title: Mapped[str] = mapped_column(String(96), default="")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)

    department: Mapped[Department | None] = relationship()
    branch: Mapped[Branch | None] = relationship()
    shift: Mapped[Shift | None] = relationship()
    face_profiles: Mapped[list["FaceProfile"]] = relationship(back_populates="employee")


class FaceProfile(Base):
    """
    One active embedding per employee. `revoked_at` soft-deletes it: removing a face profile must
    never remove attendance history.

    In production `embedding` is a pgvector column; JSON here keeps the PoC on SQLite.
    """

    __tablename__ = "face_profiles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    employee_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("employees.id"), nullable=False, index=True
    )
    embedding: Mapped[list[float]] = mapped_column(JSON, nullable=False)
    model_name: Mapped[str] = mapped_column(String(64), nullable=False)
    model_version: Mapped[str] = mapped_column(String(32), nullable=False)
    quality_score: Mapped[float] = mapped_column(Float, default=0.0)
    consent_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    employee: Mapped[Employee] = relationship(back_populates="face_profiles")


class AttendanceRecord(Base):
    __tablename__ = "attendance_records"
    __table_args__ = (
        UniqueConstraint("organization_id", "employee_id", "date", name="uq_attendance_day"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("organizations.id"), nullable=False, index=True
    )
    employee_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("employees.id"), nullable=False, index=True
    )
    date: Mapped[date] = mapped_column(Date, nullable=False, index=True)
    check_in_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    check_out_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[AttendanceStatus] = mapped_column(
        Enum(AttendanceStatus), default=AttendanceStatus.PRESENT
    )
    late_minutes: Mapped[int] = mapped_column(Integer, default=0)
    match_confidence: Mapped[float] = mapped_column(Float, default=0.0)
    liveness_score: Mapped[float] = mapped_column(Float, default=0.0)
    device_label: Mapped[str] = mapped_column(String(96), default="")


class AuditLog(Base):
    """Append-only trail. Never store embeddings, tokens or passwords in `meta`."""

    __tablename__ = "audit_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    organization_id: Mapped[str | None] = mapped_column(String(36), nullable=True, index=True)
    actor_user_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    action: Mapped[str] = mapped_column(String(64), nullable=False)
    target_type: Mapped[str] = mapped_column(String(48), default="")
    target_id: Mapped[str] = mapped_column(String(36), default="")
    meta: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now, index=True)
