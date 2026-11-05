export class ApiError extends Error {
  constructor(status, problem) {
    super(problem.detail || "A kérés nem sikerült.");
    this.status = status;
    this.code = problem.code;
    this.errors = problem.errors || [];
  }
}

export async function api(path, { token, body, ...options } = {}) {
  let response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new ApiError(0, {
      detail:
        "A szerver nem érhető el. Ellenőrizd a kapcsolatot, majd próbáld újra.",
    });
  }
  if (!response.ok) {
    const problem = await response
      .json()
      .catch(() => ({ detail: "A szerver nem tudta feldolgozni a kérést." }));
    if (response.status === 401 && token)
      window.dispatchEvent(new Event("eszkozpont:unauthorized"));
    throw new ApiError(response.status, problem);
  }
  return response.status === 204 ? null : response.json();
}
