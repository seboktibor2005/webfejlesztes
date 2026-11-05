import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../App";

const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => body,
});
beforeEach(() => {
  window.history.replaceState({}, "", "/belepes");
});
afterEach(() => {
  vi.unstubAllGlobals();
});
describe("magyar belépési folyamat", () => {
  it("védi a katalógust és belépést kér", async () => {
    window.history.replaceState({}, "", "/eszkozok");
    render(<App />);
    expect(
      await screen.findByRole("heading", { name: "Jó, hogy itt vagy." }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/belepes");
  });
  it("megjeleníti a szerver magyar hibáját", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          response({ detail: "Hibás e-mail-cím vagy jelszó." }, 401),
        ),
    );
    render(<App />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail-cím"), "anna@example.test");
    await user.type(screen.getByLabelText("Jelszó"), "rosszjelszo");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Hibás e-mail-cím vagy jelszó.",
    );
  });
  it("valódi belépési válaszból megnyitja a katalógust, és USER elől elrejti az adminmenüt", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) => {
        if (url === "/api/auth/login")
          return response({
            accessToken: "test-token",
            expiresAt: new Date(Date.now() + 100_000).toISOString(),
            user: { id: 1, displayName: "Minta Anna", role: "USER" },
          });
        if (url.includes("/equipment"))
          return response({
            content: [],
            page: { number: 0, totalElements: 0, totalPages: 0 },
          });
        return response([]);
      }),
    );
    render(<App />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail-cím"), "anna@example.test");
    await user.type(screen.getByLabelText("Jelszó"), "SajatJelszo2026!");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    expect(
      await screen.findByRole("heading", {
        name: "Mi kell a következő ötletedhez?",
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Felhasználók" }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText("Erre most nincs találat"),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/equipment"),
        expect.objectContaining({
          headers: { Authorization: "Bearer test-token" },
        }),
      ),
    );
  });
});
