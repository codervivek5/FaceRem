import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

import { ACCESS_COOKIE } from "@/lib/session";

/**
 * Cheap gate: bounce anonymous visitors off /dashboard before rendering, rather than making a
 * round-trip to the API to discover there is no session.
 *
 * This is convenience, not security. Authorization is decided by the API on every request; the
 * presence of a cookie proves nothing about what the holder may read.
 */
export function middleware(request: NextRequest) {
  if (request.cookies.get(ACCESS_COOKIE)) return NextResponse.next();

  const login = new URL("/login", request.url);
  login.searchParams.set("expired", "1");
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/dashboard/:path*"],
};
