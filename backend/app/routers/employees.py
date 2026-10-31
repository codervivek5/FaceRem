"""The employee roster and the face profile attached to each person."""

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..face import get_engine
from ..models import Branch, Department, Employee, FaceProfile, Shift
from ..schemas import EmployeeCreate, EmployeeOut, EmployeeUpdate, EnrollResponse
from ..security import AppError, Principal, record_audit, require_roles, tenant_scope
from ..deps import (
    assert_seat_available,
    STAFF_ROLES,
    VIEWER_ROLES,
    enrolled_ids,
    get_org_employee,
    read_image,
    revoke_active_face_profiles,
)

router = APIRouter()


def _employee_out(employee: Employee, enrolled_ids: set[str]) -> EmployeeOut:
    return EmployeeOut(
        id=employee.id,
        employee_code=employee.employee_code,
        full_name=employee.full_name,
        email=employee.email,
        phone=employee.phone,
        department=employee.department.name if employee.department else "",
        branch=employee.branch.name if employee.branch else "",
        job_title=employee.job_title,
        shift_name=employee.shift.name if employee.shift else "",
        is_active=employee.is_active,
        face_enrolled=employee.id in enrolled_ids,
    )


def _get_or_create_department(db: Session, organization_id: str, name: str) -> Department:
    """Departments are created on the fly from a plain name, the same lenient behaviour the old
    free-text `department` column had. `name` is matched case-insensitively per organization."""
    clean = (name or "").strip() or "General"
    department = db.scalar(
        select(Department).where(
            Department.organization_id == organization_id,
            func.lower(Department.name) == clean.lower(),
        )
    )
    if department is None:
        department = Department(organization_id=organization_id, name=clean)
        db.add(department)
        db.flush()
    return department


def _get_or_create_branch(db: Session, organization_id: str, name: str | None) -> Branch | None:
    clean = (name or "").strip()
    if not clean:
        return None
    branch = db.scalar(
        select(Branch).where(
            Branch.organization_id == organization_id, func.lower(Branch.name) == clean.lower()
        )
    )
    if branch is None:
        branch = Branch(organization_id=organization_id, name=clean)
        db.add(branch)
        db.flush()
    return branch


def _find_shift(db: Session, organization_id: str, name: str | None) -> Shift | None:
    """Unlike department/branch, a shift is never created implicitly: its hours matter for
    attendance calculations, so an unknown shift name is a validation error, not a guess."""
    clean = (name or "").strip()
    if not clean:
        return None
    shift = db.scalar(
        select(Shift).where(
            Shift.organization_id == organization_id, func.lower(Shift.name) == clean.lower()
        )
    )
    if shift is None:
        raise AppError("SHIFT_NOT_FOUND", f"Shift '{clean}' does not exist.", 404)
    return shift


@router.get("/employees", response_model=list[EmployeeOut])
def list_employees(
    include_inactive: bool = False,
    principal: Principal = Depends(require_roles(*VIEWER_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> list[EmployeeOut]:
    organization_id = principal.organization_id
    query = select(Employee).where(Employee.organization_id == organization_id)
    if not include_inactive:
        query = query.where(Employee.is_active.is_(True))
    employees = db.scalars(query.order_by(Employee.full_name)).all()
    enrolled = enrolled_ids(db, organization_id)
    return [_employee_out(employee, enrolled) for employee in employees]


@router.post("/employees", response_model=EmployeeOut, status_code=201)
def create_employee(
    payload: EmployeeCreate,
    request: Request,
    principal: Principal = Depends(require_roles(*STAFF_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> EmployeeOut:
    organization_id = principal.organization_id
    assert_seat_available(db, organization_id)
    duplicate = db.scalar(
        select(Employee).where(
            Employee.organization_id == organization_id,
            func.lower(Employee.employee_code) == payload.employee_code.strip().lower(),
        )
    )
    if duplicate is not None:
        raise AppError("CONFLICT", "That employee code is already in use.", 409)

    department = _get_or_create_department(db, organization_id, payload.department)
    branch = _get_or_create_branch(db, organization_id, payload.branch)
    shift = _find_shift(db, organization_id, payload.shift_name)

    employee = Employee(
        organization_id=organization_id,
        employee_code=payload.employee_code.strip(),
        full_name=payload.full_name.strip(),
        email=str(payload.email).lower(),
        phone=payload.phone.strip(),
        department_id=department.id,
        branch_id=branch.id if branch else None,
        shift_id=shift.id if shift else None,
        job_title=payload.job_title,
    )
    db.add(employee)
    db.flush()
    record_audit(
        db,
        action="employee.created",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        employee_code=employee.employee_code,
    )
    db.commit()
    return _employee_out(employee, set())


@router.get("/employees/{employee_id}", response_model=EmployeeOut)
def get_employee(
    employee_id: str,
    principal: Principal = Depends(require_roles(*VIEWER_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> EmployeeOut:
    employee = get_org_employee(db, principal.organization_id, employee_id)
    return _employee_out(employee, enrolled_ids(db, principal.organization_id))


@router.put("/employees/{employee_id}", response_model=EmployeeOut)
def update_employee(
    employee_id: str,
    payload: EmployeeUpdate,
    request: Request,
    principal: Principal = Depends(require_roles(*STAFF_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> EmployeeOut:
    """Partial update: only fields present in the payload are changed."""
    employee = get_org_employee(db, principal.organization_id, employee_id)
    changed_fields = payload.model_fields_set

    if "full_name" in changed_fields and payload.full_name is not None:
        employee.full_name = payload.full_name.strip()
    if "email" in changed_fields and payload.email is not None:
        employee.email = str(payload.email).lower()
    if "phone" in changed_fields and payload.phone is not None:
        employee.phone = payload.phone.strip()
    if "job_title" in changed_fields and payload.job_title is not None:
        employee.job_title = payload.job_title
    if "department" in changed_fields and payload.department is not None:
        employee.department_id = _get_or_create_department(
            db, employee.organization_id, payload.department
        ).id
    if "branch" in changed_fields:
        branch = _get_or_create_branch(db, employee.organization_id, payload.branch)
        employee.branch_id = branch.id if branch else None
    if "shift_name" in changed_fields:
        shift = _find_shift(db, employee.organization_id, payload.shift_name)
        employee.shift_id = shift.id if shift else None
    if "is_active" in changed_fields and payload.is_active is not None:
        employee.is_active = payload.is_active
        if not payload.is_active:
            revoke_active_face_profiles(db, employee, datetime.now(timezone.utc))

    db.flush()
    record_audit(
        db,
        action="employee.updated",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        fields=sorted(changed_fields),
    )
    db.commit()
    return _employee_out(employee, enrolled_ids(db, employee.organization_id))


@router.delete("/employees/{employee_id}", status_code=200)
def delete_employee(
    employee_id: str,
    request: Request,
    principal: Principal = Depends(require_roles(*STAFF_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> dict:
    """Soft delete: the employee row and attendance history are kept, but the employee is
    deactivated and can no longer be recognized by a scan."""
    employee = get_org_employee(db, principal.organization_id, employee_id)
    now = datetime.now(timezone.utc)
    employee.is_active = False
    revoked = revoke_active_face_profiles(db, employee, now)

    record_audit(
        db,
        action="employee.deactivated",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        revoked_profiles=revoked,
    )
    db.commit()
    return {"employee_id": employee.id, "is_active": False, "face_profiles_revoked": revoked}


@router.post("/employees/{employee_id}/face/enroll", response_model=EnrollResponse)
async def enroll_face(
    employee_id: str,
    request: Request,
    image: UploadFile = File(...),
    consent: bool = Form(...),
    principal: Principal = Depends(require_roles(*STAFF_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> EnrollResponse:
    if not consent:
        raise AppError("CONSENT_REQUIRED", "Explicit consent is required to enroll a face.", 422)

    employee = get_org_employee(db, principal.organization_id, employee_id)
    embedding = get_engine().embed(await read_image(image))

    # Re-enrollment revokes the previous profile rather than deleting the row, so the change
    # remains auditable.
    now = datetime.now(timezone.utc)
    for existing in db.scalars(
        select(FaceProfile).where(
            FaceProfile.employee_id == employee.id, FaceProfile.revoked_at.is_(None)
        )
    ).all():
        existing.revoked_at = now

    profile = FaceProfile(
        organization_id=employee.organization_id,
        employee_id=employee.id,
        embedding=embedding.vector,
        model_name=embedding.model_name,
        model_version=embedding.model_version,
        quality_score=embedding.quality,
        consent_at=now,
    )
    db.add(profile)
    db.flush()
    record_audit(
        db,
        action="face.enrolled",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        model_name=embedding.model_name,
        quality_score=round(embedding.quality, 3),
    )
    db.commit()
    return EnrollResponse(
        employee_id=employee.id,
        face_enrolled=True,
        quality_score=embedding.quality,
        model_name=embedding.model_name,
    )


@router.delete("/employees/{employee_id}/face", status_code=200)
def revoke_face(
    employee_id: str,
    request: Request,
    principal: Principal = Depends(require_roles(*STAFF_ROLES)),
    db: Session = Depends(get_db),
    _: Principal = Depends(tenant_scope),
) -> dict:
    """Removes the biometric profile. Attendance history is deliberately left untouched."""
    employee = get_org_employee(db, principal.organization_id, employee_id)
    now = datetime.now(timezone.utc)
    revoked = revoke_active_face_profiles(db, employee, now)

    record_audit(
        db,
        action="face.revoked",
        principal=principal,
        target_type="employee",
        target_id=employee.id,
        request=request,
        revoked_profiles=revoked,
    )
    db.commit()
    return {"employee_id": employee.id, "revoked_profiles": revoked, "attendance_retained": True}
