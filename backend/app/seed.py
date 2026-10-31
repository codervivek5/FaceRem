"""Demo tenants for the PoC. Two organizations exist so tenant isolation is testable."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import PLAN_SEATS, Subscription, SubscriptionStatus, Branch, Department, Employee, Organization, Role, Shift, User
from .security import hash_password

DEMO_PASSWORD = "demo12345"

#: (slug, display name, plan, employee-code prefix)
_ORGS = [
    ("northwind", "Northwind Industries", "GROWTH", "NW"),
    ("acme", "Acme Logistics", "TRIAL", "AC"),
]

_STAFF = [
    ("admin@{slug}.demo", "Org Admin", Role.ORG_ADMIN),
    ("hr@{slug}.demo", "HR Admin", Role.HR_ADMIN),
    ("manager@{slug}.demo", "Team Manager", Role.MANAGER),
    ("kiosk@{slug}.demo", "Lobby Kiosk", Role.ATTENDANCE_DEVICE),
]

_EMPLOYEES = [
    ("E-1001", "Aarav Sharma", "Engineering", "Android Engineer"),
    ("E-1002", "Priya Nair", "People & HR", "HR Business Partner"),
    ("E-1003", "Rahul Verma", "Operations", "Shift Supervisor"),
]


def seed_demo_data(db: Session) -> None:
    if db.scalar(select(Organization).limit(1)) is not None:
        return

    for slug, name, plan, code_prefix in _ORGS:
        organization = Organization(name=name, slug=slug, plan=plan)
        db.add(organization)
        db.flush()
        db.add(
            Subscription(
                organization_id=organization.id,
                plan=plan,
                seat_limit=PLAN_SEATS.get(plan, 25),
                status=SubscriptionStatus.ACTIVE,
            )
        )

        for email_template, full_name, role in _STAFF:
            db.add(
                User(
                    organization_id=organization.id,
                    email=email_template.format(slug=slug),
                    password_hash=hash_password(DEMO_PASSWORD),
                    full_name=f"{full_name} ({name})",
                    role=role,
                )
            )

        departments: dict[str, Department] = {}
        branch = Branch(organization_id=organization.id, name="Head Office", city=name.split()[0])
        shift = Shift(organization_id=organization.id, name="General Shift", start_time="09:00")
        db.add(branch)
        db.add(shift)
        db.flush()

        for index, (code, full_name, department_name, title) in enumerate(_EMPLOYEES):
            department = departments.get(department_name)
            if department is None:
                department = Department(organization_id=organization.id, name=department_name)
                db.add(department)
                db.flush()
                departments[department_name] = department

            employee = Employee(
                organization_id=organization.id,
                employee_code=f"{code_prefix}-{code}",
                full_name=full_name,
                email=f"{full_name.split()[0].lower()}@{slug}.demo",
                phone="",
                department_id=department.id,
                # Only the first employee is assigned a branch/shift, so the rest exercise the
                # "unassigned" path the client also has to handle.
                branch_id=branch.id if index == 0 else None,
                shift_id=shift.id if index == 0 else None,
                job_title=title,
            )
            db.add(employee)
            db.flush()

            # One self-service login so the EMPLOYEE role can be exercised.
            if code == "E-1001":
                db.add(
                    User(
                        organization_id=organization.id,
                        email=f"employee@{slug}.demo",
                        password_hash=hash_password(DEMO_PASSWORD),
                        full_name=full_name,
                        role=Role.EMPLOYEE,
                        employee_id=employee.id,
                    )
                )

    db.add(
        User(
            organization_id=None,
            email="root@faceattend.platform",
            password_hash=hash_password(DEMO_PASSWORD),
            full_name="Platform Operator",
            role=Role.SUPER_ADMIN,
        )
    )
    db.commit()
