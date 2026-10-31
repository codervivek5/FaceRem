import "server-only";

import { redirect } from "next/navigation";

import { getAccessToken } from "./session";

export const API_BASE_URL = process.env.API_BASE_URL ?? "http://127.0.0.1:8000";

/** The envelope every FastAPI response uses. */
type Envelope<T> =
  | { success: true; data: T; message: string }
  | { success: false; error: { code: string; message: string } };

export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit & { token?: string | null } = {},
): Promise<T> {
  const { token, ...rest } = init;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      ...(rest.headers ?? {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: "no-store",
  });

  let body: Envelope<T>;
  try {
    body = (await response.json()) as Envelope<T>;
  } catch {
    throw new ApiError("NETWORK_ERROR", "The server returned an unreadable response.", response.status);
  }

  if (!response.ok || !body.success) {
    const error = "error" in body ? body.error : undefined;
    throw new ApiError(
      error?.code ?? "HTTP_ERROR",
      error?.message ?? "The request could not be completed.",
      response.status,
    );
  }
  return body.data;
}

/**
 * Authenticated call from a Server Component. An expired or missing session sends the user to
 * the login page rather than rendering a broken screen.
 */
export async function apiGet<T>(path: string): Promise<T> {
  const token = await getAccessToken();
  if (!token) redirect("/login");

  try {
    return await apiFetch<T>(path, { token });
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.code === "TOKEN_EXPIRED")) {
      redirect("/login?expired=1");
    }
    throw error;
  }
}
