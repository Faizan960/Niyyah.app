/** Small HTTP helpers shared by the router. */

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

export function errorResponse(status: number, message: string, code?: string): Response {
  return json({ error: message, code: code ?? null }, status);
}

/** Thrown anywhere in request handling; mapped to a safe JSON error by the router. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
  ) {
    super(message);
    this.name = "HttpError";
  }
}
