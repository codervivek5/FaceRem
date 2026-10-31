from datetime import date, datetime
from typing import Literal

from typing import Annotated

from pydantic import BaseModel, EmailStr, Field

from .models import AttendanceStatus, Role, SubscriptionStatus


class LoginRequest(BaseModel):
    email: str
    password: str
    organization_slug: str | None = None


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


# bcrypt silently truncates beyond 72 bytes, so a longer password would not be fully
# checked. Reject it instead of pretending to store it.
Password = Annotated[str, Field(min_length=8, max_length=72)]


class SignupRequest(BaseModel):
    """Self-serve onboarding: creates the organization and its first ORG_ADMIN together."""

    organization_name: str = Field(min_length=2, max_length=160)
    #: URL-safe tenant handle used at sign-in. Derived from the name when omitted.
    organization_slug: str | None = Field(default=None, min_length=2, max_length=64)
    full_name: str = Field(min_length=2, max_length=160)
    email: EmailStr
    password: Password


class SignupResponse(BaseModel):
    organization_id: str
    organization_slug: str
    user_id: str
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: Password


class SubscriptionOut(BaseModel):
    plan: str
    status: SubscriptionStatus
    seat_limit: int
    seats_used: int
    seats_available: int
    current_period_end: datetime | None


class UserCreate(BaseModel):
    full_name: str = Field(min_length=2, max_length=160)
    email: EmailStr
    password: Password
    role: Role
    #: Links a self-service EMPLOYEE login to its employee record.
    employee_id: str | None = None


class UserUpdate(BaseModel):
    full_name: str | None = Field(default=None, min_length=2, max_length=160)
    role: Role | None = None
    is_active: bool | None = None


class UserOut(BaseModel):
    id: str
    full_name: str
    email: str
    role: Role
    employee_id: str | None
    is_active: bool
    created_at: datetime


class MeResponse(BaseModel):
    id: str
    full_name: str
    email: str
    role: Role
    organization_id: str | None
    organization_name: str | None
    employee_id: str | None


class EmployeeCreate(BaseModel):
    employee_code: str = Field(min_length=1, max_length=48)
    full_name: str = Field(min_length=1, max_length=160)
    email: EmailStr
    phone: str = ""
    department: str = "General"
    branch: str | None = None
    shift_name: str | None = None
    job_title: str = ""


class EmployeeUpdate(BaseModel):
    """All fields optional: only the ones present are changed."""

    full_name: str | None = Field(default=None, min_length=1, max_length=160)
    email: EmailStr | None = None
    phone: str | None = None
    department: str | None = None
    branch: str | None = None
    shift_name: str | None = None
    job_title: str | None = None
    is_active: bool | None = None


class EmployeeOut(BaseModel):
    id: str
    employee_code: str
    full_name: str
    email: str
    phone: str
    department: str
    branch: str
    job_title: str
    shift_name: str
    is_active: bool
    face_enrolled: bool


class DepartmentCreate(BaseModel):
    name: str = Field(min_length=1, max_length=96)


class DepartmentOut(BaseModel):
    id: str
    name: str
    head_count: int


class BranchCreate(BaseModel):
    name: str = Field(min_length=1, max_length=96)
    city: str = ""


class BranchOut(BaseModel):
    id: str
    name: str
    city: str
    head_count: int


class ShiftCreate(BaseModel):
    name: str = Field(min_length=1, max_length=96)
    start_time: str = Field(default="09:00", pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    end_time: str = Field(default="17:00", pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    grace_minutes: int = Field(default=10, ge=0, le=180)


class ShiftOut(BaseModel):
    id: str
    name: str
    start_time: str
    end_time: str
    grace_minutes: int


class EnrollResponse(BaseModel):
    employee_id: str
    face_enrolled: bool
    quality_score: float
    model_name: str


class RecognizeResponse(BaseModel):
    employee_id: str
    employee_code: str
    full_name: str
    department: str
    #: CHECK_IN, CHECK_OUT, or ALREADY_PRESENT when the day was already recorded. The last of
    #: these writes nothing: an unattended kiosk can be scanned repeatedly without side effects.
    event: Literal["CHECK_IN", "CHECK_OUT", "ALREADY_PRESENT"]
    recorded_at: datetime
    status: AttendanceStatus
    late_minutes: int
    match_confidence: float
    liveness_score: float
    #: The day's timestamps so a repeat scan can say "already in since 09:05".
    check_in_at: datetime | None = None
    check_out_at: datetime | None = None


class AttendanceOut(BaseModel):
    id: str
    employee_id: str
    employee_name: str
    date: date
    check_in_at: datetime | None
    check_out_at: datetime | None
    status: AttendanceStatus
    late_minutes: int


class DashboardResponse(BaseModel):
    organization_name: str
    date: date
    total_employees: int
    present: int
    late: int
    not_checked_in: int
    pending_enrollments: int


class AuditLogOut(BaseModel):
    id: str
    action: str
    actor_user_id: str | None
    target_type: str
    target_id: str
    meta: dict
    created_at: datetime
