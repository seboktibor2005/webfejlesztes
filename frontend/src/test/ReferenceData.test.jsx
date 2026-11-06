import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReferenceDataPage } from "../management/ReferenceDataPage";
import { ReservationPage } from "../pages/ReservationPage";

let actor;
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ token: "test-token", user: actor }),
}));
const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => structuredClone(body),
});
let records, kind;
function setupFetch(mutate = () => response({})) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options) => {
      if (["POST", "PUT", "PATCH", "DELETE"].includes(options.method))
        return mutate(url, options);
      if (url.startsWith(`/api/admin/${kind}?`))
        return response({
          content: records,
          page: {
            totalPages: records.length ? 1 : 0,
            totalElements: records.length,
          },
        });
      return response(records[0]);
    }),
  );
}
function renderPage(element = <ReferenceDataPage kind={kind} />, path = "/") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={element} />
          <Route path="/foglalasok/:id" element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
const writes = () =>
  fetch.mock.calls.filter(([, options]) =>
    ["POST", "PUT", "PATCH", "DELETE"].includes(options.method),
  );
beforeEach(() => {
  actor = { id: 99, role: "ADMIN" };
  kind = "categories";
  records = [
    { id: 1, name: "Fotó", description: "Kamerák", active: true, version: 3 },
  ];
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe("törzsadatok", () => {
  it("új kategóriát készít szerverkezelt mezők beküldése nélkül", async () => {
    setupFetch((url, options) => {
      const created = {
        ...JSON.parse(options.body),
        id: 2,
        active: true,
        version: 0,
      };
      records = [...records, created];
      return response(created, 201);
    });
    renderPage();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Név"), "Fejlesztés");
    await user.type(screen.getByLabelText("Leírás"), "Fejlesztőkészletek.");
    await user.click(screen.getByRole("button", { name: "Törzsadat mentése" }));
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(writes()[0][0]).toBe("/api/admin/categories");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      name: "Fejlesztés",
      description: "Fejlesztőkészletek.",
    });
  });

  it("szerkesztéskor az eredeti verziót küldi és nem állít aktivitást", async () => {
    setupFetch((url, options) => {
      records[0] = { ...records[0], ...JSON.parse(options.body), version: 4 };
      return response(records[0]);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Fotó szerkesztése" }),
    );
    await user.clear(screen.getByLabelText("Név"));
    await user.type(screen.getByLabelText("Név"), "Fotó és videó");
    await user.click(screen.getByRole("button", { name: "Törzsadat mentése" }));
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      name: "Fotó és videó",
      description: "Kamerák",
      version: 3,
    });
  });

  it("leírás nélküli kategóriát is megnyit és elment", async () => {
    records[0].description = null;
    setupFetch(() => response({ ...records[0], version: 4 }));
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Fotó szerkesztése" }),
    );
    expect(screen.getByLabelText("Leírás")).toHaveValue("");
    await user.click(screen.getByRole("button", { name: "Törzsadat mentése" }));
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      name: "Fotó",
      description: "",
      version: 3,
    });
    expect(screen.getByLabelText("Leírás")).toHaveValue("");
  });

  it("archiválás előtt külön megerősítést kér, és verziózott parancsot küld", async () => {
    setupFetch(() => {
      records[0] = { ...records[0], active: false, version: 4 };
      return response(null, 204);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Fotó archiválása" }),
    );
    expect(writes()).toHaveLength(0);
    expect(
      screen.getByText(/A kapcsolódó eszközök eltűnnek/),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(
      await screen.findByText("Archivált", { selector: "span" }),
    ).toBeInTheDocument();
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      active: false,
      version: 3,
    });
  });

  it("a hivatkozott rekord törlését érthető hibával jelzi, és megtartja a piszkozatot", async () => {
    setupFetch(() =>
      response(
        {
          code: "REFERENCE_IN_USE",
          detail:
            "A kategóriához eszköz tartozik. Törlés helyett archiválható.",
        },
        409,
      ),
    );
    renderPage();
    const user = userEvent.setup();
    await user.type(
      screen.getByLabelText("Név"),
      "Új, még nem mentett kategória",
    );
    await user.click(
      await screen.findByRole("button", { name: "Fotó törlése" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "eszköz tartozik",
    );
    expect(screen.getByLabelText("Név")).toHaveValue(
      "Új, még nem mentett kategória",
    );
    expect(writes()[0][0]).toBe("/api/admin/categories/1?version=3");
    expect(
      screen.queryByText("A nem hivatkozott törzsadat törölve."),
    ).not.toBeInTheDocument();
  });

  it("nem hivatkozott rekord törlése után frissíti a listát", async () => {
    setupFetch(() => {
      records = [];
      return response(null, 204);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Fotó törlése" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Nincs ilyen törzsadat." }),
    ).toBeInTheDocument();
  });

  it("helyszínnél az összes címmezőt és a kódot is elmenti", async () => {
    kind = "locations";
    records = [
      {
        id: 2,
        code: "MEDIA",
        name: "Médiaműhely",
        building: "A",
        room: "101",
        active: true,
        version: 2,
      },
    ];
    setupFetch((url, options) => {
      records[0] = { ...records[0], ...JSON.parse(options.body), version: 3 };
      return response(records[0]);
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Médiaműhely szerkesztése" }),
    );
    await user.clear(screen.getByLabelText("Helyiség"));
    await user.type(screen.getByLabelText("Helyiség"), "204");
    await user.click(screen.getByRole("button", { name: "Törzsadat mentése" }));
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      name: "Médiaműhely",
      code: "MEDIA",
      building: "A",
      room: "204",
      version: 2,
    });
  });

  it("verzióütközés után csak kérésre állítja vissza a friss adatokat", async () => {
    setupFetch(() => {
      records[0] = { ...records[0], name: "Másik admin neve", version: 4 };
      return response(
        {
          code: "STALE_VERSION",
          detail: "A törzsadat időközben megváltozott.",
        },
        409,
      );
    });
    renderPage();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Fotó szerkesztése" }),
    );
    await user.clear(screen.getByLabelText("Név"));
    await user.type(screen.getByLabelText("Név"), "Saját piszkozat");
    await user.click(screen.getByRole("button", { name: "Törzsadat mentése" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "időközben megváltozott",
    );
    expect(screen.getByLabelText("Név")).toHaveValue("Saját piszkozat");
    await user.click(
      screen.getByRole("button", { name: "Friss adatok betöltése" }),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Név")).toHaveValue("Másik admin neve"),
    );
  });

  it("az állapotszűrést a szervernek küldi", async () => {
    setupFetch();
    renderPage();
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("Állapot"), "false");
    await waitFor(() =>
      expect(
        fetch.mock.calls.some(([url]) => url.includes("active=false")),
      ).toBe(true),
    );
  });
});

it("a lejárati eseményt magyarul, rendszereseményként mutatja", async () => {
  actor = { id: 1, role: "USER" };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url) =>
      response(
        url.includes("/events")
          ? {
              content: [
                {
                  id: 1,
                  eventType: "RESERVATION_EXPIRED",
                  actor: null,
                  occurredAt: "2026-11-05T10:00:00Z",
                  note: "Az igény időszaka átvétel nélkül véget ért.",
                },
              ],
              page: { totalPages: 1 },
            }
          : {
              id: 8,
              equipment: { name: "Kamera", inventoryCode: "FOTO-001" },
              requester: { id: 1, displayName: "Minta Anna" },
              startsAt: "2026-11-05T08:00:00Z",
              endsAt: "2026-11-05T10:00:00Z",
              purpose: "Egyetemi projekt",
              status: "EXPIRED",
              version: 1,
              loan: null,
            },
      ),
    ),
  );
  renderPage(<ReservationPage />, "/foglalasok/8");
  expect(await screen.findByText("Átvétel nélkül lejárt")).toBeInTheDocument();
  expect(screen.getByText(/Rendszer ·/)).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Visszavonás" }),
  ).not.toBeInTheDocument();
});
