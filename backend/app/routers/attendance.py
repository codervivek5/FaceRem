"""Recognition scans, the records they produce, and what reads them back."""

from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..face import check_liveness, find_best_match, get_engine
from ..models import (
    AttendanceRecord,
    AttendanceStatus,
    AuditLog,
    Employee,
    Organization,
    Role,
    Shift,
)
from ..schemas import AttendanceOut, AuditLogOut, DashboardResponse, RecognizeResponse
from ..security import (
    AppError,
    Principal,
    get_principal,
    record_audit,
    require_roles,
    tenant_scope,
)
from ..deps import SCANNER_ROLES, VIEWER_ROLES, enrolled_ids, read_image

settings = get_settings()
router = APIRouter()


@router.post("/attendance/recognize", response_model=RecognizeResponse)
async def recognize(
    request: Request,
    image: UploadFile = File(...),
    liveness_score: float | None = Form(default=None),
    device_label: str = Form(default=""),
    principal: Principal = Depends(require_roles(*SCANNER_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> RecognizeResponse:
    organization_id = principal.organization_id
    liveness = check_liveness(liveness_score)
    embedding = get_engine().embed(await read_image(image))
    match = find_best_match(db, organization_id, embedding.vector)

    if match is None:
        record_audit(
            db,
            action="attendance.face_not_recognized",
            principal=principal,
            request=request,
            device_label=device_label,
        )
        db.commit()
        # No near-match information is returned: an unknown face learns nothing about the roster.
        raise AppError("FACE_NOT_RECOGNIZED", "Face could not be recognized.", 404)

    employee = match.employee
    now = datetime.now(timezone.utc)
    today = now.date()
    record = db.scalar(
        select(AttendanceRecord).where(
            AttendanceRecord.organization_id == organization_id,
            AttendanceRecord.employee_id == employee.id,
            AttendanceRecord.date == today,
        )
    )

    event = _resolve_event(record, now)

    if event == "ALREADY_PRESENT":
        # The day is already recorded, so nothing is written and nothing is audited. A gate
        # kiosk scans whoever stands in front of it, all day: turning those repeats into rows
        # would bury the real events. The caller still gets the employee and their times so it
        # can greet them by name.
        return _recognize_response(employee, record, event, now, match.confidence, liveness.score)

    if event == "CHECK_IN":
        late_minutes = _late_minutes(now, employee.shift)
        record = AttendanceRecord(
            organization_id=organization_id,
            employee_id=employee.id,
            date=today,
            check_in_at=now,
            status=AttendanceStatus.LATE if late_minutes > 0 else AttendanceStatus.PRESENT,
            late_minutes=late_minutes,
        )
        db.add(record)
    else:
        record.check_out_at = now

    record.match_confidence = match.confidence
    record.liveness_score = liveness.score
    record.device_label = device_label

    db.flush()
    record_audit(
        db,
        action=f"attendance.{event.lower()}",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        match_confidence=round(match.confidence, 4),
        liveness_provider=liveness.provider,
        device_label=device_label,
    )
    db.commit()

    return _recognize_response(employee, record, event, now, match.confidence, liveness.score)


def _resolve_event(record: AttendanceRecord | None, now: datetime) -> str:
    """
    Decide what a scan means for someone who may already have been seen today.

    The gate is unattended, so the rule has to be safe when the same person passes it several
    times: only a scan well after check-in ends the day, everything in between is a no-op.
    """
    if record is None:
        return "CHECK_IN"
    if record.check_out_at is not None:
        return "ALREADY_PRESENT"
    if record.check_in_at is None:
        return "CHECK_IN"

    since_check_in = (now - record.check_in_at.replace(tzinfo=timezone.utc)).total_seconds() / 60
    if since_check_in < settings.checkout_after_minutes:
        return "ALREADY_PRESENT"
    return "CHECK_OUT"


def _recognize_response(
    employee: Employee,
    record: AttendanceRecord | None,
    event: str,
    now: datetime,
    confidence: float,
    liveness_score: float,
) -> RecognizeResponse:
    return RecognizeResponse(
        employee_id=employee.id,
        employee_code=employee.employee_code,
        full_name=employee.full_name,
        department=employee.department.name if employee.department else "",
        event=event,
        recorded_at=now,
        status=record.status if record else AttendanceStatus.PRESENT,
        late_minutes=record.late_minutes if record else 0,
        match_confidence=confidence,
        liveness_score=liveness_score,
        check_in_at=record.check_in_at if record else None,
        check_out_at=record.check_out_at if record else None,
    )


def _late_minutes(moment: datetime, shift: Shift | None) -> int:
    """Uses the employee's assigned shift when set, otherwise the org-wide default from config."""
    if shift is not None:
        hour, minute = (int(part) for part in shift.start_time.split(":"))
        grace_minutes = shift.grace_minutes
    else:
        hour, minute = settings.shift_start_hour, 0
        grace_minutes = settings.shift_grace_minutes

    start = moment.replace(hour=hour, minute=minute, second=0, microsecond=0) + timedelta(
        minutes=grace_minutes
    )
    if moment <= start:
        return 0
    return int((moment - start).total_seconds() // 60)


@router.get("/attendance", response_model=list[AttendanceOut])
def list_attendance(
    on: date | None = None,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> list[AttendanceOut]:
    """
    Staff see the whole organization. An EMPLOYEE sees only their own rows, enforced here rather
    than by the client.
    """
    query = (
        select(AttendanceRecord, Employee)
        .join(Employee, Employee.id == AttendanceRecord.employee_id)
        .where(AttendanceRecord.organization_id == principal.organization_id)
    )
    if on is not None:
        query = query.where(AttendanceRecord.date == on)

    if principal.role is Role.EMPLOYEE:
        if principal.employee_id is None:
            return []
        query = query.where(AttendanceRecord.employee_id == principal.employee_id)
    elif principal.role not in VIEWER_ROLES:
        raise AppError("FORBIDDEN", "You do not have permission to perform this action.", 403)

    rows = db.execute(query.order_by(AttendanceRecord.date.desc())).all()
    return [
        AttendanceOut(
            id=record.id,
            employee_id=record.employee_id,
            employee_name=employee.full_name,
            date=record.date,
            check_in_at=record.check_in_at,
            check_out_at=record.check_out_at,
            status=record.status,
            late_minutes=record.late_minutes,
        )
        for record, employee in rows
    ]


@router.get("/dashboard", response_model=DashboardResponse)
def dashboard(
    principal: Principal = Depends(require_roles(*VIEWER_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> DashboardResponse:
    organization_id = principal.organization_id
    organization = db.get(Organization, organization_id)
    today = datetime.now(timezone.utc).date()

    total = db.scalar(
        select(func.count())
        .select_from(Employee)
        .where(Employee.organization_id == organization_id, Employee.is_active.is_(True))
    )
    records = db.scalars(
        select(AttendanceRecord).where(
            AttendanceRecord.organization_id == organization_id,
            AttendanceRecord.date == today,
        )
    ).all()
    present = sum(1 for r in records if r.status is AttendanceStatus.PRESENT)
    late = sum(1 for r in records if r.status is AttendanceStatus.LATE)
    enrolled = enrolled_ids(db, organization_id)

    return DashboardResponse(
        organization_name=organization.name if organization else "",
        date=today,
        total_employees=total or 0,
        present=present,
        late=late,
        not_checked_in=max(0, (total or 0) - len(records)),
        pending_enrollments=max(0, (total or 0) - len(enrolled)),
    )


@router.get("/audit-logs", response_model=list[AuditLogOut])
def list_audit_logs(
    limit: int = 50,
    principal: Principal = Depends(require_roles(Role.ORG_ADMIN)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> list[AuditLogOut]:
    logs = db.scalars(
        select(AuditLog)
        .where(AuditLog.organization_id == principal.organization_id)
        .order_by(AuditLog.created_at.desc())
        .limit(min(limit, 200))
    ).all()
    return [
        AuditLogOut(
            id=log.id,
            action=log.action,
            actor_user_id=log.actor_user_id,
            target_type=log.target_type,
            target_id=log.target_id,
            meta=log.meta or {},
            created_at=log.created_at,
        )
        for log in logs
    ]
