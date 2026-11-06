import {
  BrowserRouter,
  Link,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { AppLayout } from "./components/AppLayout";
import { AuthPage } from "./pages/AuthPage";
import { CatalogPage } from "./pages/CatalogPage";
import { EquipmentPage } from "./pages/EquipmentPage";
import { UsersPage } from "./pages/UsersPage";
import { ReservationsPage } from "./pages/ReservationsPage";
import { ReservationPage } from "./pages/ReservationPage";
import { LoansPage } from "./pages/LoansPage";
import { EquipmentEditorPage } from "./management/EquipmentEditorPage";
import { UserEditorPage } from "./management/UserEditorPage";
import { ReferenceDataPage } from "./management/ReferenceDataPage";
import {
  MaintenancePage,
  MaintenanceCreatePage,
  MaintenanceDetailPage,
} from "./management/MaintenancePages";

const client = new QueryClient({
  defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
});
function Protected({ roles }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user)
    return (
      <Navigate
        to="/belepes"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  if (roles && !roles.includes(user.role))
    return (
      <main className="state-panel">
        <h1>Ehhez nincs jogosultságod.</h1>
        <p>Ez az oldal másik jogosultsági szinthez tartozik.</p>
        <Link className="button primary" to="/eszkozok">
          Vissza a katalógushoz
        </Link>
      </main>
    );
  return <Outlet />;
}
export default function App() {
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/belepes" element={<AuthPage key="login" />} />
            <Route
              path="/regisztracio"
              element={<AuthPage key="register" register />}
            />
            <Route element={<Protected />}>
              <Route element={<AppLayout />}>
                <Route
                  path="/eszkozok"
                  element={<CatalogPage key="catalog" />}
                />
                <Route path="/eszkozok/:id" element={<EquipmentPage />} />
                <Route
                  path="/foglalasok"
                  element={<ReservationsPage key="mine" />}
                />
                <Route path="/foglalasok/:id" element={<ReservationPage />} />
                <Route element={<Protected roles={["MANAGER", "ADMIN"]} />}>
                  <Route path="/kolcsonzesek" element={<LoansPage />} />
                  <Route path="/kezeles/uj" element={<EquipmentEditorPage />} />
                  <Route
                    path="/kezeles/:id"
                    element={<EquipmentEditorPage />}
                  />
                  <Route path="/karbantartasok" element={<MaintenancePage />} />
                  <Route
                    path="/karbantartasok/uj"
                    element={<MaintenanceCreatePage />}
                  />
                  <Route
                    path="/karbantartasok/:id"
                    element={<MaintenanceDetailPage />}
                  />
                  <Route
                    path="/elbiralas"
                    element={<ReservationsPage key="review" management />}
                  />
                  <Route
                    path="/kezeles"
                    element={<CatalogPage key="management" management />}
                  />
                </Route>
                <Route element={<Protected roles={["ADMIN"]} />}>
                  <Route
                    path="/torzsadatok"
                    element={<Navigate to="/torzsadatok/kategoriak" replace />}
                  />
                  <Route
                    path="/torzsadatok/kategoriak"
                    element={
                      <ReferenceDataPage key="categories" kind="categories" />
                    }
                  />
                  <Route
                    path="/torzsadatok/helyszinek"
                    element={
                      <ReferenceDataPage key="locations" kind="locations" />
                    }
                  />
                  <Route path="/felhasznalok" element={<UsersPage />} />
                  <Route
                    path="/felhasznalok/:id"
                    element={<UserEditorPage />}
                  />
                </Route>
              </Route>
            </Route>
            <Route path="/" element={<Navigate to="/eszkozok" replace />} />
            <Route
              path="*"
              element={
                <main className="state-panel">
                  <span className="eyebrow">404</span>
                  <h1>Ez az oldal nincs az eszköztárban.</h1>
                  <Link className="button primary" to="/eszkozok">
                    Vissza a katalógushoz
                  </Link>
                </main>
              }
            />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
