import { NextResponse } from "next/server";

import { ApiError, apiFetch } from "@/lib/api";
import { ACCESS_COOKIE, REFRESH_COOKIE, cookieOptions } from "@/lib/session";

interface TokenPair {
  access_token: string;
  refresh_token: string;
}

const ACCESS_MAX_AGE = 30 * 60;
const REFRESH_MAX_AGE = 14 * 24 * 60 * 60;

export async function POST(request: Request) {
  const { email, password, organization_slug } = await request.json();

  try {
    const tokens = await apiFetch<TokenPair>("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, organization_slug: organization_slug || null }),
    });

    // Tokens go straight into httpOnly cookies and are never returned to the browser.
    const response = NextResponse.json({ ok: true });
    response.cookies.set(ACCESS_COOKIE, tokens.access_token, cookieOptions(ACCESS_MAX_AGE));
    response.cookies.set(REFRESH_COOKIE, tokens.refresh_token, cookieOptions(REFRESH_MAX_AGE));
    return response;
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json({ ok: false, message: error.message }, { status: error.status });
    }
    return NextResponse.json(
      { ok: false, message: "Could not reach the attendance service." },
      { status: 502 },
    );
  }
}
