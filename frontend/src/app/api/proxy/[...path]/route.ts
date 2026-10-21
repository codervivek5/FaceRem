import { NextResponse } from "next/server";

import { API_BASE_URL } from "@/lib/api";
import { getAccessToken } from "@/lib/session";

/**
 * Authenticated passthrough for calls that must start in the browser: creating an employee,
 * uploading a captured frame for enrollment, running a scan, and revoking a face profile.
 * The token is attached here, server-side, so it never enters JavaScript.
 *
 * This is a per-method allowlist, not an open relay: anything not listed is refused, so a future
 * endpoint cannot be reached from the browser by accident.
 */
const ALLOWED: Record<string, RegExp[]> = {
  POST: [
    /^api\/v1\/employees$/,
    /^api\/v1\/employees\/[\w-]+\/face\/enroll$/,
    /^api\/v1\/attendance\/recognize$/,
  ],
  DELETE: [/^api\/v1\/employees\/[\w-]+\/face$/],
};

async function forward(request: Request, path: string[], method: "POST" | "DELETE") {
  const target = path.join("/");

  if (!ALLOWED[method].some((pattern) => pattern.test(target))) {
    return NextResponse.json(
      { success: false, error: { code: "FORBIDDEN", message: "This endpoint is not proxied." } },
      { status: 403 },
    );
  }

  const token = await getAccessToken();
  if (!token) {
    return NextResponse.json(
      { success: false, error: { code: "UNAUTHORIZED", message: "Your session has ended." } },
      { status: 401 },
    );
  }

  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  const contentType = request.headers.get("content-type");
  // Preserve the multipart boundary, or the JSON type, from the original request.
  if (contentType) headers["Content-Type"] = contentType;

  const upstream = await fetch(`${API_BASE_URL}/${target}`, {
    method,
    headers,
    body: method === "DELETE" ? undefined : await request.arrayBuffer(),
  });

  return new NextResponse(await upstream.text(), {
    status: upstream.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function POST(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return forward(request, (await context.params).path, "POST");
}

export async function DELETE(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return forward(request, (await context.params).path, "DELETE");
}
