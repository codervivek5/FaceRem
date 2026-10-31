import json
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from sqlalchemy import inspect
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from .routers import attendance, auth, employees, org, users
from .config import get_settings
from .db import SessionLocal, engine
from .security import AppError
from .seed import seed_demo_data

settings = get_settings()


def _assert_schema_exists() -> None:
    """
    Fail loudly if migrations have not been run.

    Alembic owns the schema now. Creating tables here as well would let the two drift
    apart silently, which is the failure mode that costs you a database.
    """
    if not inspect(engine).has_table("organizations"):
        raise RuntimeError(
            "Database schema is missing. Run:  alembic upgrade head"
        )


@asynccontextmanager
async def lifespan(app: FastAPI):
    _assert_schema_exists()
    if settings.seed_demo_data:
        with SessionLocal() as db:
            seed_demo_data(db)
    yield


app = FastAPI(
    title="FaceAttend API",
    version="0.1.0",
    description="Multi-tenant facial attendance PoC.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if not settings.is_production else [],
    allow_methods=["*"],
    allow_headers=["*"],
)


def _error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"success": False, "error": {"code": code, "message": message}},
    )


@app.exception_handler(AppError)
async def handle_app_error(_: Request, exc: AppError) -> JSONResponse:
    return _error(exc.status_code, exc.code, exc.message)


@app.exception_handler(StarletteHTTPException)
async def handle_http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
    codes = {401: "UNAUTHORIZED", 403: "FORBIDDEN", 404: "NOT_FOUND", 429: "RATE_LIMITED"}
    return _error(exc.status_code, codes.get(exc.status_code, "HTTP_ERROR"), str(exc.detail))


@app.exception_handler(RequestValidationError)
async def handle_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
    return _error(422, "VALIDATION_ERROR", "Please check the submitted values and try again.")


@app.middleware("http")
async def envelope_response(request: Request, call_next):
    """Wraps successful JSON bodies in the project envelope. Errors are shaped by the handlers."""
    response = await call_next(request)
    if request.url.path in {"/docs", "/openapi.json", "/redoc"}:
        return response
    if response.status_code >= 400 or response.headers.get("content-type") != "application/json":
        return response

    body = b"".join([chunk async for chunk in response.body_iterator])
    if not body:
        return response

    payload = json.loads(body)
    if isinstance(payload, dict) and "success" in payload:
        return JSONResponse(status_code=response.status_code, content=payload)
    return JSONResponse(
        status_code=response.status_code,
        content={"success": True, "data": payload, "message": "Operation successful"},
    )


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "face_engine": settings.face_engine, "liveness": settings.liveness_mode}


for _router, _tag in (
    (auth.router, "auth"),
    (employees.router, "employees"),
    (attendance.router, "attendance"),
    (org.router, "organization"),
    (users.router, "users"),
):
    app.include_router(_router, prefix="/api/v1", tags=[_tag])
