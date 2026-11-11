import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CatalogPage } from "../pages/CatalogPage";
import { EquipmentPage } from "../pages/EquipmentPage";
import { AvailabilityCalendar } from "../availability/AvailabilityCalendar";
import { CatalogPeriodFilter } from "../availability/CatalogPeriodFilter";
import {
  addCalendarDays,
  inputPeriod,
  intervalsOnDay,
  weekDays,
  weekStart,
} from "../availability/calendar";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    token: "test-token",
    user: { id: 2, displayName: "Minta Anna", role: "USER" },
  }),
}));
const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => structuredClone(body),
});
const equipment = {
  id: 1,
  name: "Canon kamera",
  inventoryCode: "FOTO-001",
  lifecycle: "ACTIVE",
  description: "Egyetemi kamera.",
  category: { id: 3, name: "Fotó" },
  location: { id: 4, name: "Médiaműhely", building: "A", room: "101" },
};
const START = "2026-11-12T08:00:00.000Z";
const END = "2026-11-12T11:00:00.000Z";
let intervalData;
function setupFetch(override) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options) => {
      const custom = override?.(url, options);
      if (custom) return custom;
      if (url.includes("/availability?")) {
        const params = new URL(url, "http://localhost").searchParams;
        return response({
          equipmentId: 1,
          from: params.get("from"),
          to: params.get("to"),
          checkedAt: "2026-11-08T10:00:00Z",
          blockingIntervals: intervalData,
          overdueLoan: false,
          overdueMaintenance: false,
        });
      }
      if (url.startsWith("/api/equipment?"))
        return response({
          content: [equipment],
          page: { number: 0, totalPages: 1, totalElements: 1 },
        });
      if (url === "/api/equipment/1") return response(equipment);
      if (url === "/api/categories") return response([equipment.category]);
      if (url === "/api/locations") return response([equipment.location]);
      return response([]);
    }),
  );
}
function LocationProbe() {
  const location = useLocation();
  return (
    <output aria-label="Aktuális cím">
      {location.pathname}
      {location.search}
    </output>
  );
}
function mount(element, path = "/") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={element} />
          <Route path="/eszkozok" element={<CatalogPage />} />
          <Route path="/eszkozok/:id" element={<EquipmentPage />} />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
function fillPeriod(start = "2026-11-12T09:00", end = "2026-11-12T12:00") {
  fireEvent.input(screen.getByLabelText("Átvétel a kereséshez"), {
    target: { value: start },
  });
  fireEvent.input(screen.getByLabelText("Visszahozás a kereséshez"), {
    target: { value: end },
  });
}
const searchCalls = () =>
  fetch.mock.calls.filter(([url]) => url.startsWith("/api/equipment?"));
beforeEach(() => {
  intervalData = [];
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe("időszakra szűrt katalógus", () => {
  it("csak alkalmazáskor küldi a budapesti időszakot, megtartja a szűrőket és törli az oldalszámot", async () => {
    setupFetch();
    mount(
      <CatalogPage />,
      "/eszkozok?categoryId=3&locationId=4&sort=name,desc&page=2",
    );
    await screen.findByText("Canon kamera");
    const before = searchCalls().length;
    await userEvent.click(screen.getByText("Szűrés időszakra"));
    fillPeriod();
    expect(searchCalls()).toHaveLength(before);
    await userEvent.click(
      screen.getByRole("button", { name: "Szabad eszközök keresése" }),
    );
    await waitFor(() =>
      expect(
        searchCalls().some(([url]) =>
          new URL(url, "http://localhost").searchParams.has("startsAt"),
        ),
      ).toBe(true),
    );
    const query = new URL(searchCalls().at(-1)[0], "http://localhost")
      .searchParams;
    expect(Object.fromEntries(query)).toMatchObject({
      startsAt: START,
      endsAt: END,
      page: "0",
      categoryId: "3",
      locationId: "4",
      sort: "name,desc",
    });
    expect(
      await screen.findByText("Az időszakra igényelhető"),
    ).toBeInTheDocument();
  });

  it("URL-ből helyreállítja a dátumokat, és csak az időszakot törli", async () => {
    setupFetch();
    const params = new URLSearchParams({
      startsAt: START,
      endsAt: END,
      categoryId: "3",
    });
    mount(<CatalogPage />, `/eszkozok?${params}`);
    expect(screen.getByLabelText("Átvétel a kereséshez")).toHaveValue(
      "2026-11-12T09:00",
    );
    await screen.findByText("Az időszakra igényelhető");
    await userEvent.click(
      screen.getByRole("button", { name: "Időszak törlése" }),
    );
    await screen.findByText("Canon kamera");
    expect(
      screen.queryByText("Az időszakra igényelhető"),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("Aktuális cím")).toHaveTextContent(
      "/eszkozok?categoryId=3",
    );
    expect(screen.getByLabelText("Átvétel a kereséshez")).toHaveValue("");
  });

  it("fordított és óraátállításkor nem létező időpontot nem alkalmaz", async () => {
    const apply = vi.fn();
    mount(<CatalogPeriodFilter onApply={apply} onClear={vi.fn()} />);
    await userEvent.click(screen.getByText("Szűrés időszakra"));
    fillPeriod("2026-11-12T12:00", "2026-11-12T09:00");
    await userEvent.click(
      screen.getByRole("button", { name: "Szabad eszközök keresése" }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "visszahozás az átvétel után",
    );
    fillPeriod("2027-03-28T02:30", "2027-03-28T05:00");
    await userEvent.click(
      screen.getByRole("button", { name: "Szabad eszközök keresése" }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("óraátállítás");
    expect(apply).not.toHaveBeenCalled();
  });

  it("szerverhibánál megtartja az időpontokat, és nem mutat régi szabad találatot", async () => {
    setupFetch((url) =>
      url.includes("startsAt=") && url.startsWith("/api/equipment?")
        ? response(
            { detail: "A kezdés túl messze van.", code: "INVALID_PERIOD" },
            400,
          )
        : null,
    );
    mount(<CatalogPage />);
    await screen.findByText("Canon kamera");
    await userEvent.click(screen.getByText("Szűrés időszakra"));
    fillPeriod();
    await userEvent.click(
      screen.getByRole("button", { name: "Szabad eszközök keresése" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("túl messze");
    expect(screen.getByLabelText("Átvétel a kereséshez")).toHaveValue(
      "2026-11-12T09:00",
    );
    expect(screen.queryByText("Canon kamera")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Az időszakra igényelhető"),
    ).not.toBeInTheDocument();
  });

  it("az adatlapra és az igénylőűrlapba is továbbviszi az alkalmazott időszakot", async () => {
    setupFetch();
    const params = new URLSearchParams({ startsAt: START, endsAt: END });
    mount(<CatalogPage />, `/eszkozok?${params}`);
    await userEvent.click(
      await screen.findByRole("link", { name: "Canon kamera részletei" }),
    );
    expect(await screen.findByLabelText("Átvétel időpontja")).toHaveValue(
      "2026-11-12T09:00",
    );
    expect(screen.getByLabelText("Visszahozás időpontja")).toHaveValue(
      "2026-11-12T12:00",
    );
    expect(screen.getByLabelText("Mire használnád az eszközt?")).toHaveValue(
      "",
    );
    expect(
      screen
        .getByRole("link", { name: "Vissza az eszközökhöz" })
        .getAttribute("href"),
    ).toContain("startsAt=");
    await screen.findByRole("heading", { name: "Foglaltsági naptár" });
  });

  it("a kezelői leltárlistát továbbra is teljes nyilvántartásként mutatja", async () => {
    setupFetch((url) =>
      url.startsWith("/api/management/equipment?")
        ? response({
            content: [equipment],
            page: { totalPages: 1, totalElements: 1 },
          })
        : null,
    );
    mount(<CatalogPage management />);
    await screen.findByText("Canon kamera");
    expect(
      screen.queryByRole("button", { name: "Szabad eszközök keresése" }),
    ).not.toBeInTheDocument();
    expect(
      fetch.mock.calls.some(([url]) =>
        url.startsWith("/api/management/equipment?"),
      ),
    ).toBe(true);
  });
});

describe("foglaltsági naptár", () => {
  it("a napokon átnyúló foglaltságot napokra bontja, és hetet vált", async () => {
    intervalData = [
      {
        startsAt: "2026-11-12T22:00:00Z",
        endsAt: "2026-11-13T01:00:00Z",
        kind: "RESERVATION",
      },
      {
        startsAt: "2026-11-14T09:00:00Z",
        endsAt: "2026-11-14T11:00:00Z",
        kind: "MAINTENANCE",
      },
    ];
    setupFetch();
    mount(<AvailabilityCalendar equipmentId={1} initialStart={START} />);
    expect(await screen.findByText("23:00–24:00")).toBeInTheDocument();
    expect(screen.getByText("00:00–02:00")).toBeInTheDocument();
    expect(screen.getByText("10:00–12:00")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "Következő hét" }),
    );
    await waitFor(() =>
      expect(
        fetch.mock.calls.some(
          ([url]) =>
            new URL(url, "http://localhost").searchParams.get("from") ===
            "2026-11-15T23:00:00.000Z",
        ),
      ).toBe(true),
    );
    expect(
      fetch.mock.calls.every(
        ([, options]) => !options.method || options.method === "GET",
      ),
    ).toBe(true);
  });

  it("késedelmes kölcsönzésnél az üresnek látszó napokat sem ígéri igényelhetőnek", async () => {
    setupFetch((url) =>
      url.includes("/availability?")
        ? response({
            checkedAt: START,
            blockingIntervals: [],
            overdueLoan: true,
            overdueMaintenance: true,
          })
        : null,
    );
    mount(<AvailabilityCalendar equipmentId={1} initialStart={START} />);
    expect(
      await screen.findByText("Az eszköz jelenleg nem igényelhető."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Késedelmes visszahozás miatt/),
    ).toBeInTheDocument();
    expect(screen.getByText(/A karbantartás túllépte/)).toBeInTheDocument();
    expect(screen.queryByText("Nincs jelzett akadály")).not.toBeInTheDocument();
  });

  it("betöltési hiba után újrapróbálható", async () => {
    let fail = true;
    setupFetch((url) =>
      url.includes("/availability?") && fail
        ? response({ detail: "A naptár most nem elérhető." }, 503)
        : null,
    );
    mount(<AvailabilityCalendar equipmentId={1} initialStart={START} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("nem elérhető");
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "Újrapróbálom" }));
    await waitFor(() =>
      expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
    );
    expect(screen.getByText(/Pillanatnyi állapot/)).toBeInTheDocument();
  });
});

describe("budapesti naptári napok", () => {
  it("az éjfél utáni budapesti napból számolja a hétfőt", () => {
    expect(weekStart("2026-11-08T23:30:00Z")).toBe("2026-11-09");
    expect(addCalendarDays("2026-12-31", 1)).toBe("2027-01-01");
  });
  it("a nyári óraátállítás hetét 167 valós óraként kéri le", () => {
    const days = weekDays("2027-03-22");
    expect(
      (Date.parse(days[6].end) - Date.parse(days[0].start)) / 3_600_000,
    ).toBe(167);
  });
  it("a téli óraátállítás hetét 169 valós óraként kéri le", () => {
    const days = weekDays("2026-10-19");
    expect(
      (Date.parse(days[6].end) - Date.parse(days[0].start)) / 3_600_000,
    ).toBe(169);
  });
  it("a pontos éjfélkor végződő intervallum nem foglalja a következő napot", () => {
    const day = weekDays("2026-11-09")[1];
    expect(
      intervalsOnDay(
        [
          {
            startsAt: "2026-11-09T20:00:00Z",
            endsAt: day.start,
            kind: "RESERVATION",
          },
        ],
        day,
      ),
    ).toEqual([]);
  });
  it("hibás URL-időpontból nem készít érvénytelen űrlapértéket", () => {
    expect(inputPeriod({ startsAt: "hibas", endsAt: null })).toEqual({
      startsAt: "",
      endsAt: "",
    });
  });
});
