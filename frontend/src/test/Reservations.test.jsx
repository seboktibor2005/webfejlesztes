import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReservationForm } from "../reservations/ReservationForm";
import { ReservationPage } from "../pages/ReservationPage";

let actor;
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ token: "test-token", user: actor }),
}));
const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => body,
});
const reservation = {
  id: 42,
  equipment: { id: 1, name: "Canon EOS R50", inventoryCode: "FOTO-001" },
  requester: { id: 1, displayName: "Minta Anna" },
  startsAt: "2026-11-12T08:00:00Z",
  endsAt: "2026-11-12T11:00:00Z",
  purpose: "Egyetemi interjúprojekt felvétele.",
  status: "REQUESTED",
  version: 0,
};
function renderPage(element, path = "/") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={element} />
          <Route
            path="/foglalasok/:id"
            element={path === "/" ? <h1>Igény elkészült</h1> : element}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  actor = { id: 1, role: "USER" };
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe("foglalás és elbírálás felülete", () => {
  it("az igényt budapesti időpontból, tulajdonos és állapot beküldése nélkül adja fel", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(response({ id: 42 }, 201)),
    );
    renderPage(<ReservationForm equipmentId={1} />);
    fireEvent.change(screen.getByLabelText("Átvétel időpontja"), {
      target: { value: "2026-11-12T09:00" },
    });
    fireEvent.change(screen.getByLabelText("Visszahozás időpontja"), {
      target: { value: "2026-11-12T12:00" },
    });
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Mire használnád az eszközt?"),
      "Egyetemi interjúprojekt felvétele.",
    );
    await user.click(
      screen.getByRole("button", { name: "Foglalási igény küldése" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Igény elkészült" }),
    ).toBeInTheDocument();
    const [url, options] = fetch.mock.calls[0];
    expect(url).toBe("/api/reservations");
    expect(JSON.parse(options.body)).toEqual({
      equipmentId: 1,
      startsAt: "2026-11-12T08:00:00.000Z",
      endsAt: "2026-11-12T11:00:00.000Z",
      purpose: "Egyetemi interjúprojekt felvétele.",
    });
  });

  it("ütközés esetén megtartja az igénylési űrlap adatait", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          response(
            { detail: "Erre az időszakra már van jóváhagyott foglalás." },
            409,
          ),
        ),
    );
    renderPage(<ReservationForm equipmentId={1} />);
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Mire használnád az eszközt?"),
      "Egyetemi interjúprojekt felvétele.",
    );
    await user.click(
      screen.getByRole("button", { name: "Foglalási igény küldése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "már van jóváhagyott foglalás",
    );
    expect(screen.getByLabelText("Mire használnád az eszközt?")).toHaveValue(
      "Egyetemi interjúprojekt felvétele.",
    );
  });

  it("saját igénynél a kezelőnek sem kínál jóváhagyást", async () => {
    actor = { id: 1, role: "MANAGER" };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url) =>
        response(
          url.includes("/events")
            ? { content: [], page: { totalPages: 0 } }
            : reservation,
        ),
      ),
    );
    renderPage(<ReservationPage />, "/foglalasok/42");
    expect(
      await screen.findByRole("heading", { name: "Canon EOS R50" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Jóváhagyás" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/Saját igényedről másik/)).toBeInTheDocument();
  });

  it("megerősítést kér, futó döntést nem dupláz, majd frissíti az állapotot", async () => {
    actor = { id: 2, role: "MANAGER" };
    let resolveDecision;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url, options) => {
        if (options.method === "POST")
          return new Promise((resolve) => {
            resolveDecision = resolve;
          });
        return response(
          url.includes("/events")
            ? { content: [], page: { totalPages: 0 } }
            : reservation,
        );
      }),
    );
    renderPage(<ReservationPage />, "/foglalasok/42");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Jóváhagyás" }));
    expect(
      fetch.mock.calls.filter(([, options]) => options.method === "POST"),
    ).toHaveLength(0);
    await user.click(
      screen.getByRole("button", { name: "Döntés megerősítése" }),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Mentés…" })).toBeDisabled(),
    );
    expect(
      fetch.mock.calls.filter(([, options]) => options.method === "POST"),
    ).toHaveLength(1);
    resolveDecision(
      response({ ...reservation, status: "APPROVED", version: 1 }),
    );
    expect(await screen.findByText("Jóváhagyva")).toBeInTheDocument();
    expect(await screen.findByRole("status")).toHaveTextContent(
      "A művelet sikerült",
    );
  });

  it("a jóváhagyási ütközést érthető hibával jelzi, sikerüzenet nélkül", async () => {
    actor = { id: 2, role: "MANAGER" };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url, options) => {
        if (options.method === "POST")
          return response(
            {
              detail:
                "Az eszközre már van jóváhagyott foglalás ebben az időszakban.",
            },
            409,
          );
        return response(
          url.includes("/events")
            ? { content: [], page: { totalPages: 0 } }
            : reservation,
        );
      }),
    );
    renderPage(<ReservationPage />, "/foglalasok/42");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Jóváhagyás" }));
    await user.click(
      screen.getByRole("button", { name: "Döntés megerősítése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "már van jóváhagyott foglalás",
    );
    expect(screen.queryByText(/A művelet sikerült/)).not.toBeInTheDocument();
  });
});
