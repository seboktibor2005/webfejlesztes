import { afterEach, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { DashboardPage } from "../pages/DashboardPage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const response = (body, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => body,
});

const personal = {
  checkedAt: "2026-10-25T10:00:00Z",
  pendingCount: 2,
  activeLoanCount: 1,
  nextPickup: {
    reservationId: 17,
    equipmentName: "Canon kamera",
    inventoryCode: "FOTO-001",
    at: "2026-10-25T12:00:00Z",
  },
  nextReturn: {
    reservationId: 21,
    equipmentName: "RØDE mikrofon",
    inventoryCode: "HANG-001",
    at: "2026-10-25T09:00:00Z",
  },
};

const management = {
  checkedAt: personal.checkedAt,
  date: "2026-10-25",
  historyFrom: "2026-09-26",
  pendingCount: 4,
  todayPickupCount: 2,
  todayReturnCount: 3,
  overdueLoanCount: 1,
  maintenanceCount: 5,
  loansByCategory: [
    { categoryId: 1, categoryName: "Fotó és videó", count: 7 },
    { categoryId: 2, categoryName: "Hangtechnika", count: 3 },
  ],
};

function mount(role, fetchMock) {
  useAuth.mockReturnValue({ user: { id: 1, role }, token: "test-token" });
  vi.stubGlobal("fetch", fetchMock);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

it("a hallgatónak csak a saját teendőit és a késés jelzését mutatja", async () => {
  const fetchMock = vi.fn().mockResolvedValue(response(personal));
  mount("USER", fetchMock);
  expect(await screen.findByText("Canon kamera")).toBeInTheDocument();
  expect(screen.getByText("RØDE mikrofon")).toBeInTheDocument();
  expect(screen.getByText("Késésben")).toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "Következő átvétel részletei" }),
  ).toHaveAttribute("href", "/foglalasok/17");
  expect(
    screen.getByRole("link", { name: "Legkorábbi visszahozás részletei" }),
  ).toHaveAttribute("href", "/foglalasok/21");
  expect(screen.queryByText("Átadások kategóriánként")).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
    "/api/dashboard/me",
    expect.objectContaining({
      headers: { Authorization: "Bearer test-token" },
    }),
  );
});

it("teendő nélküli hallgatónak érthető üres állapotot mutat", async () => {
  mount(
    "USER",
    vi.fn().mockResolvedValue(
      response({
        ...personal,
        pendingCount: 0,
        activeLoanCount: 0,
        nextPickup: null,
        nextReturn: null,
      }),
    ),
  );
  expect(
    await screen.findByText("Nincs soron következő átvételed."),
  ).toBeInTheDocument();
  expect(
    screen.getByText("Jelenleg nincs nálad kölcsönzött eszköz."),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "Eszközök keresése" }),
  ).toHaveAttribute("href", "/eszkozok");
  expect(
    screen.queryByRole("link", { name: /részletei/ }),
  ).not.toBeInTheDocument();
});

it("az eszközfelelős a napi számlálókat és a szerver kategóriaösszesítését látja", async () => {
  const fetchMock = vi.fn().mockResolvedValue(response(management));
  mount("MANAGER", fetchMock);
  const tasks = await screen.findByRole("region", {
    name: "Eszköztári teendők",
  });
  expect(
    within(tasks).getByText("Ma esedékes visszahozás").nextElementSibling,
  ).toHaveTextContent("3");
  expect(
    within(tasks).getByText("Késésben lévő kölcsönzés").nextElementSibling,
  ).toHaveTextContent("1");
  const history = screen.getByRole("region", {
    name: "Átadások kategóriánként",
  });
  expect(within(history).getByText("Fotó és videó")).toBeInTheDocument();
  expect(within(history).getByText("7 átadás")).toBeInTheDocument();
  expect(within(history).getByText("3 átadás")).toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Felhasználók" }),
  ).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
    "/api/management/dashboard",
    expect.objectContaining({
      headers: { Authorization: "Bearer test-token" },
    }),
  );
});

it("az admin a kezelői nézet mellett az adminisztrációs linkeket is megkapja", async () => {
  mount(
    "ADMIN",
    vi.fn().mockResolvedValue(response({ ...management, loansByCategory: [] })),
  );
  expect(
    await screen.findByText("Ebben az időszakban nem történt átadás."),
  ).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Felhasználók" })).toHaveAttribute(
    "href",
    "/felhasznalok",
  );
  expect(screen.getByRole("link", { name: "Törzsadatok" })).toHaveAttribute(
    "href",
    "/torzsadatok",
  );
});

it("a hibás lekérést újrapróbálja, majd friss szerveradatot jelenít meg", async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      response({ detail: "Az áttekintés most nem érhető el." }, 503),
    )
    .mockResolvedValueOnce(response(personal))
    .mockResolvedValueOnce(response({ ...personal, pendingCount: 3 }));
  mount("USER", fetchMock);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Az áttekintés most nem érhető el.",
  );
  await userEvent.click(screen.getByRole("button", { name: "Újrapróbálom" }));
  expect(await screen.findByText("Canon kamera")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Frissítés" }));
  expect(await screen.findByText("3", { selector: "dd" })).toBeInTheDocument();
});
