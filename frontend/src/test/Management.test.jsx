import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EquipmentEditorPage } from "../management/EquipmentEditorPage";
import { UserEditorPage } from "../management/UserEditorPage";
import {
  MaintenanceCreatePage,
  MaintenanceDetailPage,
  MaintenancePage,
} from "../management/MaintenancePages";

let actor;
const endSession = vi.fn();
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ token: "test-token", user: actor, endSession }),
}));
const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => structuredClone(body),
});
const category = { id: 1, name: "Fotó" };
const location = { id: 2, name: "Médiaműhely" };
let equipment, account, period;
function setupFetch(mutate = () => response({})) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options) => {
      if (["POST", "PUT", "PATCH"].includes(options.method))
        return mutate(url, options);
      if (url.includes("/events"))
        return response({ content: [], page: { totalPages: 0 } });
      if (url === "/api/categories") return response([category]);
      if (url === "/api/locations") return response([location]);
      if (url.startsWith("/api/management/equipment?"))
        return response({
          content: [equipment],
          page: { totalPages: 1, totalElements: 1 },
        });
      if (url.startsWith("/api/management/equipment/"))
        return response(equipment);
      if (url.startsWith("/api/admin/users/")) return response(account);
      if (url.startsWith("/api/management/maintenance?"))
        return response({
          content: [period],
          page: { totalPages: 1, totalElements: 1 },
        });
      if (url.startsWith("/api/management/maintenance/"))
        return response(period);
      throw new Error(`Nem várt tesztkérés: ${url}`);
    }),
  );
}
function renderPage(path) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/kezeles/uj" element={<EquipmentEditorPage />} />
          <Route path="/kezeles/:id" element={<EquipmentEditorPage />} />
          <Route path="/felhasznalok/:id" element={<UserEditorPage />} />
          <Route path="/karbantartasok" element={<MaintenancePage />} />
          <Route
            path="/karbantartasok/uj"
            element={<MaintenanceCreatePage />}
          />
          <Route
            path="/karbantartasok/:id"
            element={<MaintenanceDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
const writes = () =>
  fetch.mock.calls.filter(([, options]) =>
    ["POST", "PUT", "PATCH"].includes(options.method),
  );
beforeEach(() => {
  actor = { id: 99, role: "ADMIN" };
  endSession.mockReset();
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-12T09:00:00Z"));
  equipment = {
    id: 5,
    name: "Canon EOS R50",
    inventoryCode: "FOTO-001",
    description: "Kamera",
    category,
    location,
    lifecycle: "ACTIVE",
    version: 3,
  };
  account = {
    id: 1,
    displayName: "Minta Anna",
    email: "anna@example.test",
    role: "USER",
    enabled: true,
    version: 2,
  };
  period = {
    id: 8,
    equipment,
    createdBy: { id: 99, displayName: "Kezelő" },
    startsAt: "2026-11-13T08:00:00Z",
    endsAt: "2026-11-13T11:00:00Z",
    reason: "Műszaki ellenőrzés",
    status: "OPEN",
    overdue: false,
  };
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("eszköznyilvántartás", () => {
  it("az új eszközt csak a módosítható mezőkkel küldi be és megnyitja az adatlapját", async () => {
    setupFetch((url, options) => {
      const body = JSON.parse(options.body);
      equipment = {
        ...equipment,
        ...body,
        inventoryCode: body.inventoryCode.toUpperCase(),
        version: 0,
      };
      return response(equipment, 201);
    });
    renderPage("/kezeles/uj");
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("Leltári szám"), "new-9");
    await user.type(screen.getByLabelText("Eszköz neve"), "Új kamera");
    await user.selectOptions(screen.getByLabelText("Kategória"), "1");
    await user.selectOptions(screen.getByLabelText("Tárolási hely"), "2");
    await user.click(screen.getByRole("button", { name: "Eszköz mentése" }));
    expect(
      await screen.findByRole("heading", { name: "Eszköz szerkesztése." }),
    ).toBeInTheDocument();
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      inventoryCode: "new-9",
      name: "Új kamera",
      description: "",
      categoryId: 1,
      locationId: 2,
    });
  });

  it("a szerkesztés verziót küld, a leltári szám olvasható marad", async () => {
    setupFetch((url, options) => {
      equipment = { ...equipment, ...JSON.parse(options.body), version: 4 };
      return response(equipment);
    });
    renderPage("/kezeles/5");
    const user = userEvent.setup();
    const name = await screen.findByLabelText("Eszköz neve");
    expect(screen.getByLabelText("Leltári szám")).toHaveAttribute("readonly");
    await user.clear(name);
    await user.type(name, "Pontosított név");
    await user.click(screen.getByRole("button", { name: "Eszköz mentése" }));
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      name: "Pontosított név",
      description: "Kamera",
      categoryId: 1,
      locationId: 2,
      version: 3,
    });
  });

  it("verzióütközésnél megőrzi a szerkesztést, és csak kérésre tölti be a friss adatokat", async () => {
    setupFetch(() => {
      equipment = { ...equipment, name: "Másik kezelő neve", version: 4 };
      return response(
        { code: "STALE_VERSION", detail: "Az eszköz időközben megváltozott." },
        409,
      );
    });
    renderPage("/kezeles/5");
    const user = userEvent.setup();
    const name = await screen.findByLabelText("Eszköz neve");
    await user.clear(name);
    await user.type(name, "Saját piszkozat");
    await user.click(screen.getByRole("button", { name: "Eszköz mentése" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "időközben megváltozott",
    );
    expect(name).toHaveValue("Saját piszkozat");
    await user.click(
      screen.getByRole("button", { name: "Friss adatok betöltése" }),
    );
    await waitFor(() => expect(name).toHaveValue("Másik kezelő neve"));
  });

  it("kivonás előtt indokot és külön megerősítést kér", async () => {
    setupFetch(() => {
      equipment = { ...equipment, lifecycle: "RETIRED", version: 4 };
      return response(equipment);
    });
    renderPage("/kezeles/5");
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Eszköz kivonása" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Kivonás megerősítése" }),
    );
    expect(writes()).toHaveLength(0);
    await user.type(
      screen.getByLabelText("Kivonás indoka"),
      "Tartós meghibásodás miatt.",
    );
    await user.click(
      screen.getByRole("button", { name: "Kivonás megerősítése" }),
    );
    expect(await screen.findByText("Kivonva")).toBeInTheDocument();
    expect(writes()[0][0]).toBe("/api/management/equipment/5/retire");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      version: 3,
      reason: "Tartós meghibásodás miatt.",
    });
  });
});

describe("felhasználószerkesztés", () => {
  it("áttekintés után módosít nevet és szerepet, az e-mailt nem küldi be", async () => {
    setupFetch((url, options) => {
      account = { ...account, ...JSON.parse(options.body), version: 3 };
      return response(account);
    });
    renderPage("/felhasznalok/1");
    const user = userEvent.setup();
    const name = await screen.findByLabelText("Megjelenített név");
    await user.clear(name);
    await user.type(name, "Pontosított Anna");
    await user.selectOptions(screen.getByLabelText("Szerepkör"), "MANAGER");
    await user.click(
      screen.getByRole("button", { name: "Változtatások áttekintése" }),
    );
    expect(writes()).toHaveLength(0);
    await user.click(
      screen.getByRole("button", { name: "Módosítás megerősítése" }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent("mentve");
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      displayName: "Pontosított Anna",
      role: "MANAGER",
      enabled: true,
      version: 2,
    });
    expect(endSession).not.toHaveBeenCalled();
  });

  it("az utolsó admin védelmét hibaüzenettel jelzi, siker nélkül", async () => {
    account = { ...account, role: "ADMIN" };
    setupFetch(() =>
      response(
        {
          code: "LAST_ADMIN",
          detail: "Az utolsó aktív adminisztrátor nem tiltható le.",
        },
        409,
      ),
    );
    renderPage("/felhasznalok/1");
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText("A fiók aktív"));
    await user.click(
      screen.getByRole("button", { name: "Változtatások áttekintése" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Módosítás megerősítése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "utolsó aktív adminisztrátor",
    );
    expect(
      screen.queryByText(/A felhasználó adatai mentve/),
    ).not.toBeInTheDocument();
  });

  it("saját módosítás után lezárja a kliens munkamenetét is", async () => {
    actor = { id: 1, role: "ADMIN" };
    account = { ...account, role: "ADMIN" };
    setupFetch(() =>
      response({ ...account, displayName: "Saját új név", version: 3 }),
    );
    renderPage("/felhasznalok/1");
    const user = userEvent.setup();
    const name = await screen.findByLabelText("Megjelenített név");
    await user.clear(name);
    await user.type(name, "Saját új név");
    await user.click(
      screen.getByRole("button", { name: "Változtatások áttekintése" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Módosítás megerősítése" }),
    );
    await waitFor(() =>
      expect(endSession).toHaveBeenCalledWith(
        expect.stringContaining("saját fiókod módosult"),
      ),
    );
  });
});

describe("karbantartás-kezelés", () => {
  async function fillMaintenance(user) {
    await screen.findByRole("option", { name: "Canon EOS R50 · FOTO-001" });
    await user.selectOptions(await screen.findByLabelText("Eszköz"), "5");
    fireEvent.input(screen.getByLabelText("Karbantartás kezdete"), {
      target: { value: "2026-11-13T09:00" },
    });
    fireEvent.input(screen.getByLabelText("Tervezett befejezés"), {
      target: { value: "2026-11-13T12:00" },
    });
    await user.type(
      screen.getByLabelText("Karbantartás indoka"),
      "Időszakos műszaki ellenőrzés.",
    );
  }
  it("a keresett eszközhöz budapesti időből küldi be a karbantartást", async () => {
    setupFetch((url, options) => {
      const body = JSON.parse(options.body);
      period = { ...period, ...body };
      return response(period, 201);
    });
    renderPage("/karbantartasok/uj");
    const user = userEvent.setup();
    await fillMaintenance(user);
    await user.click(
      screen.getByRole("button", { name: "Karbantartás mentése" }),
    );
    expect(
      await screen.findByRole("heading", { name: "A javítás részletei" }),
    ).toBeInTheDocument();
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      equipmentId: 5,
      startsAt: "2026-11-13T08:00:00.000Z",
      endsAt: "2026-11-13T11:00:00.000Z",
      reason: "Időszakos műszaki ellenőrzés.",
    });
  });

  it("ütközésnél megtartja az időszakot és az indokot", async () => {
    setupFetch(() =>
      response({ detail: "Az időszakot jóváhagyott foglalás blokkolja." }, 409),
    );
    renderPage("/karbantartasok/uj");
    const user = userEvent.setup();
    await fillMaintenance(user);
    await user.click(
      screen.getByRole("button", { name: "Karbantartás mentése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "jóváhagyott foglalás",
    );
    expect(screen.getByLabelText("Karbantartás indoka")).toHaveValue(
      "Időszakos műszaki ellenőrzés.",
    );
    expect(screen.getByLabelText("Karbantartás kezdete")).toHaveValue(
      "2026-11-13T09:00",
    );
  });

  it("a jövőbeli karbantartást indokkal mondja le, nem kínál lezárást", async () => {
    setupFetch(() => {
      period = { ...period, status: "CANCELLED" };
      return response(period);
    });
    renderPage("/karbantartasok/8");
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", {
        name: "Tervezett karbantartás lemondása",
      }),
    );
    expect(
      screen.queryByRole("button", { name: "Karbantartás lezárása" }),
    ).not.toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(writes()).toHaveLength(0);
    await user.type(
      screen.getByLabelText("Lemondás indoka"),
      "Átütemezés miatt.",
    );
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(await screen.findByText("Lemondva")).toBeInTheDocument();
    expect(writes()[0][0]).toBe("/api/management/maintenance/8/cancel");
  });

  it("a túlfutott javítást lezárja, lemondani már nem lehet", async () => {
    period = {
      ...period,
      startsAt: "2026-11-11T08:00:00Z",
      endsAt: "2026-11-11T11:00:00Z",
      overdue: true,
    };
    setupFetch(() => {
      period = {
        ...period,
        status: "CLOSED",
        overdue: false,
        closedAt: "2026-11-12T09:00:00Z",
      };
      return response(period);
    });
    renderPage("/karbantartasok/8");
    const user = userEvent.setup();
    expect(
      await screen.findByText("Túlfutott, zárolás aktív"),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Karbantartás lezárása" }),
    );
    expect(
      screen.queryByRole("button", {
        name: "Tervezett karbantartás lemondása",
      }),
    ).not.toBeInTheDocument();
    await user.type(
      screen.getByLabelText("Lezárási megjegyzés (nem kötelező)"),
      "Javítva, ellenőrizve.",
    );
    await user.click(
      screen.getByRole("button", { name: "Művelet megerősítése" }),
    );
    expect(await screen.findByText("Lezárt")).toBeInTheDocument();
    expect(JSON.parse(writes()[0][1].body)).toEqual({
      note: "Javítva, ellenőrizve.",
    });
  });
});
