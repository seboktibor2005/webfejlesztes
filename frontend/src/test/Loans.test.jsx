import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReservationPage } from "../pages/ReservationPage";
import { LoansPage } from "../pages/LoansPage";

let actor;
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ token: "test-token", user: actor }),
}));
const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => structuredClone(body),
});
const base = {
  id: 42,
  equipment: { id: 1, name: "Canon EOS R50", inventoryCode: "FOTO-001" },
  requester: { id: 1, displayName: "Minta Anna" },
  startsAt: "2026-11-12T08:00:00Z",
  endsAt: "2026-11-12T11:00:00Z",
  purpose: "Egyetemi projekt felvétele.",
  status: "APPROVED",
  version: 1,
  loan: null,
  atRisk: false,
};
const loan = {
  id: 7,
  reservationId: 42,
  equipment: base.equipment,
  requester: base.requester,
  issuedBy: { id: 2, displayName: "Kezelő Dóra" },
  issuedAt: "2026-11-12T08:00:00Z",
  dueAt: base.endsAt,
  issueCondition: "GOOD",
  returnedAt: null,
  returnedBy: null,
  returnCondition: null,
  returnNote: null,
  overdue: false,
  returnedLate: false,
  overdueMinutes: 0,
  maintenance: null,
};
let current;
function setupFetch(postHandler) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options) => {
      if (options.method === "POST") return postHandler(url, options);
      if (url.includes("/events"))
        return response({ content: [], page: { totalPages: 0 } });
      if (url.startsWith("/api/management/loans?"))
        return response({
          content: [current.loan],
          page: { totalPages: 1, totalElements: 1 },
        });
      return response(current);
    }),
  );
}
function renderPage(list = false) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter
        initialEntries={[list ? "/kolcsonzesek" : "/foglalasok/42"]}
      >
        <Routes>
          <Route path="/foglalasok/:id" element={<ReservationPage />} />
          <Route path="/kolcsonzesek" element={<LoansPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  actor = { id: 2, role: "MANAGER" };
  current = structuredClone(base);
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-12T09:00:00Z"));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("átadás és visszavétel", () => {
  it("megerősítés után ad át, szerverkezelt adatok nélkül, majd frissíti az adatlapot", async () => {
    let resolveIssue;
    setupFetch(
      () =>
        new Promise((resolve) => {
          resolveIssue = () => {
            current.loan = structuredClone(loan);
            resolve(response(current.loan, 201));
          };
        }),
    );
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Eszköz átadása" }),
    );
    expect(
      fetch.mock.calls.filter(([, options]) => options.method === "POST"),
    ).toHaveLength(0);
    await user.selectOptions(screen.getByLabelText("Átadási állapot"), "WORN");
    await user.click(
      screen.getByRole("button", { name: "Rögzítés megerősítése" }),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Mentés…" })).toBeDisabled(),
    );
    const [url, options] = fetch.mock.calls.find(
      ([, options]) => options.method === "POST",
    );
    expect(url).toBe("/api/management/reservations/42/checkout");
    expect(JSON.parse(options.body)).toEqual({
      version: 1,
      issueCondition: "WORN",
    });
    resolveIssue();
    expect(
      await screen.findByRole("button", { name: "Eszköz visszavétele" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Visszavonás" }),
    ).not.toBeInTheDocument();
  });

  it("az átadási időablakon kívül tiltja az átadást", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-12T07:59:00Z"));
    setupFetch(() => response({}));
    renderPage();
    expect(
      await screen.findByRole("button", { name: "Eszköz átadása" }),
    ).toBeDisabled();
    expect(screen.getByText(/Az átadási időablak még/)).toBeInTheDocument();
  });

  it("hallgatónak megmutatja a saját késést, de kezelői műveletet nem kínál", async () => {
    actor = { id: 1, role: "USER" };
    current.loan = { ...loan, overdue: true, overdueMinutes: 61 };
    setupFetch(() => response({}));
    renderPage();
    expect(await screen.findByText("Késésben · 61 perc")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Eszköz visszavétele" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Visszavonás" }),
    ).not.toBeInTheDocument();
  });

  it("sérülésnél leírást kér, budapesti időt küld, és megjeleníti a zárolást", async () => {
    current.loan = structuredClone(loan);
    setupFetch((url, options) => {
      const body = JSON.parse(options.body);
      current = {
        ...current,
        status: "COMPLETED",
        loan: {
          ...current.loan,
          returnedAt: "2026-11-12T09:00:00Z",
          returnedBy: loan.issuedBy,
          returnCondition: "DAMAGED",
          returnNote: body.note,
          maintenance: {
            id: 9,
            status: "OPEN",
            startsAt: "2026-11-12T09:00:00Z",
            endsAt: body.maintenanceEndsAt,
            reason: "Sérülés miatti zárolás.",
          },
        },
      };
      return response(current.loan);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Eszköz visszavétele" }),
    );
    await user.selectOptions(
      screen.getByLabelText("Visszavételi állapot"),
      "DAMAGED",
    );
    await user.click(
      screen.getByRole("button", { name: "Rögzítés megerősítése" }),
    );
    expect(
      fetch.mock.calls.filter(([, options]) => options.method === "POST"),
    ).toHaveLength(0);
    await user.type(
      screen.getByLabelText("Sérülés leírása"),
      "Eltört a burkolat.",
    );
    fireEvent.change(screen.getByLabelText("Tervezett karbantartásvég"), {
      target: { value: "2026-11-13T12:00" },
    });
    await user.click(
      screen.getByRole("button", { name: "Rögzítés megerősítése" }),
    );
    expect(
      await screen.findByText("Karbantartási zárolás"),
    ).toBeInTheDocument();
    const [url, options] = fetch.mock.calls.find(
      ([, options]) => options.method === "POST",
    );
    expect(url).toBe("/api/management/loans/7/return");
    expect(JSON.parse(options.body)).toEqual({
      condition: "DAMAGED",
      note: "Eltört a burkolat.",
      maintenanceEndsAt: "2026-11-13T11:00:00.000Z",
    });
  });

  it("visszavételi ütközésnél megőrzi a megjegyzést és nem mutat sikert", async () => {
    current.loan = structuredClone(loan);
    setupFetch(() =>
      response({ detail: "Ez a kölcsönzés már lezárult." }, 409),
    );
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Eszköz visszavétele" }),
    );
    await user.type(
      screen.getByLabelText("Megjegyzés (nem kötelező)"),
      "Hiánytalan állapot.",
    );
    await user.click(
      screen.getByRole("button", { name: "Rögzítés megerősítése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("már lezárult");
    expect(screen.getByLabelText("Megjegyzés (nem kötelező)")).toHaveValue(
      "Hiánytalan állapot.",
    );
    expect(
      screen.queryByText(/A visszavétel rögzítve/),
    ).not.toBeInTheDocument();
  });

  it("a javítás lezárását is megerősítteti és frissíti a zárolás állapotát", async () => {
    current = {
      ...current,
      status: "COMPLETED",
      loan: {
        ...loan,
        returnedAt: "2026-11-12T09:00:00Z",
        returnedBy: loan.issuedBy,
        returnCondition: "DAMAGED",
        maintenance: {
          id: 9,
          startsAt: "2026-11-12T09:00:00Z",
          endsAt: "2026-11-13T09:00:00Z",
          status: "OPEN",
          reason: "Sérült eszköz.",
        },
      },
    };
    setupFetch(() => {
      current.loan.maintenance = {
        ...current.loan.maintenance,
        status: "CLOSED",
        closedAt: "2026-11-12T09:00:00Z",
      };
      return response(current.loan.maintenance);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Karbantartás lezárása" }),
    );
    await user.type(
      screen.getByLabelText("Megjegyzés (nem kötelező)"),
      "Javítva, ellenőrizve.",
    );
    await user.click(
      screen.getByRole("button", { name: "Rögzítés megerősítése" }),
    );
    expect(await screen.findByText("Lezárt karbantartás")).toBeInTheDocument();
    const [url, options] = fetch.mock.calls.find(
      ([, options]) => options.method === "POST",
    );
    expect(url).toBe("/api/management/maintenance/9/close");
    expect(JSON.parse(options.body)).toEqual({ note: "Javítva, ellenőrizve." });
  });

  it("a késéslista szerveroldali szűrőt küld és megnyitja a kapcsolódó foglalást", async () => {
    current.loan = { ...loan, overdue: true, overdueMinutes: 30 };
    setupFetch(() => response({}));
    renderPage(true);
    const user = userEvent.setup();
    expect(
      await screen.findByRole("link", { name: "Kölcsönzés #7 részletei" }),
    ).toHaveAttribute("href", "/foglalasok/42");
    await user.selectOptions(
      screen.getByLabelText("Kölcsönzések szűrése"),
      "overdue",
    );
    await waitFor(() =>
      expect(
        fetch.mock.calls.some(
          ([url]) => url.includes("open=true") && url.includes("overdue=true"),
        ),
      ).toBe(true),
    );
  });
});
