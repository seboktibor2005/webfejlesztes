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
  vi.unstubAllEnvs();
});
describe("magyar belépési folyamat", () => {
  it.each(["true", "false"])(
    "a demófiókok megjelenítésének kapcsolója %s",
    (value) => {
      vi.stubEnv("VITE_SHOW_DEMO_ACCOUNTS", value);
      render(<App />);
      expect(
        !!screen.queryByText("Bemutatófiókok helyi kipróbáláshoz"),
      ).toBe(value === "true");
    },
  );

  it("védi a katalógust és belépést kér", async () => {
    window.history.replaceState({}, "", "/eszkozok");
    render(<App />);
    expect(
      await screen.findByRole("heading", { name: "Bejelentkezés" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/belepes");
  });
  it("megjeleníti a szerver magyar hibáját", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          response({ detail: "Hibás e-mail cím vagy jelszó." }, 401),
        ),
    );
    render(<App />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail cím"), "anna@example.test");
    await user.type(screen.getByLabelText("Jelszó"), "rosszjelszo");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Hibás e-mail cím vagy jelszó.",
    );
  });
  it("belépés után visszavisz a korábban kért katalógusra, és USER elől elrejti az adminmenüt", async () => {
    window.history.replaceState({}, "", "/eszkozok");
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
    await user.type(screen.getByLabelText("E-mail cím"), "anna@example.test");
    await user.type(screen.getByLabelText("Jelszó"), "SajatJelszo2026!");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    expect(
      await screen.findByRole("heading", {
        name: "Eszközkatalógus",
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
  it("közvetlen belépés után a személyes áttekintést nyitja meg", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) => {
        if (url === "/api/auth/login")
          return response({
            accessToken: "test-token",
            expiresAt: new Date(Date.now() + 100_000).toISOString(),
            user: { id: 1, displayName: "Minta Anna", role: "USER" },
          });
        if (url === "/api/dashboard/me")
          return response({
            checkedAt: "2026-10-25T10:00:00Z",
            pendingCount: 0,
            activeLoanCount: 0,
            nextPickup: null,
            nextReturn: null,
          });
        throw new Error("Váratlan végpont: " + url);
      }),
    );
    render(<App />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail cím"), "anna@example.test");
    await user.type(screen.getByLabelText("Jelszó"), "SajatJelszo2026!");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    expect(
      await screen.findByRole("heading", { name: "Áttekintés" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Nincs soron következő átvételed."),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/attekintes");
  });
});

describe("saját fiók", () => {
  const account = {
    id: 1,
    email: "anna@example.test",
    displayName: "Minta Anna",
    role: "USER",
  };

  async function openProfile(handleRequest) {
    window.history.replaceState({}, "", "/fiokom");
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url, options) => {
        if (url === "/api/auth/login")
          return response({
            accessToken: "test-token",
            expiresAt: new Date(Date.now() + 100_000).toISOString(),
            user: account,
          });
        return handleRequest(url, options);
      }),
    );
    render(<App />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail cím"), account.email);
    await user.type(screen.getByLabelText("Jelszó"), "SajatJelszo2026!");
    await user.click(screen.getByRole("button", { name: "Bejelentkezés" }));
    await screen.findByRole("heading", { name: "Fiókom" });
    return user;
  }

  it("a megjelenített nevet a munkamenet megtartásával frissíti", async () => {
    const save = vi.fn(() => response({ ...account, displayName: "Kovács Anna" }));
    const user = await openProfile(save);
    const input = screen.getByLabelText("Megjelenített név");
    await user.clear(input);
    await user.type(input, "Kovács Anna");
    await user.click(screen.getByRole("button", { name: "Név mentése" }));
    expect(await screen.findByText("A neved frissült.")).toBeInTheDocument();
    expect(screen.getByText("Kovács Anna")).toBeInTheDocument();
    expect(save).toHaveBeenCalledWith(
      "/api/me",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ displayName: "Kovács Anna" }),
      }),
    );
  });

  it("eltérő megerősítést jelez, sikeres jelszócsere után új belépést kér", async () => {
    const change = vi.fn(() => response(null, 204));
    const user = await openProfile(change);
    const confirmation = screen.getByLabelText("Új jelszó megerősítése");
    const submit = screen.getByRole("button", { name: "Jelszó módosítása" });
    await user.type(screen.getByLabelText("Jelenlegi jelszó"), "SajatJelszo2026!");
    await user.type(screen.getByLabelText("Új jelszó", { exact: true }), "MasikJelszo2026!");
    await user.type(confirmation, "ElirasJelszo2026!");
    await user.click(submit);
    expect(screen.getByRole("alert")).toHaveTextContent("A két új jelszó nem egyezik.");
    expect(change).not.toHaveBeenCalled();
    await user.clear(confirmation);
    await user.type(confirmation, "MasikJelszo2026!");
    await user.click(submit);
    expect(await screen.findByRole("heading", { name: "Bejelentkezés" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Lépj be az új jelszóval.");
    expect(change).toHaveBeenCalledTimes(1);
    expect(change).toHaveBeenCalledWith(
      "/api/me/password",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          currentPassword: "SajatJelszo2026!",
          newPassword: "MasikJelszo2026!",
        }),
      }),
    );
  });
});
