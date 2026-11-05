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
                <Route element={<Protected roles={["MANAGER", "ADMIN"]} />}>
                  <Route
                    path="/kezeles"
                    element={<CatalogPage key="management" management />}
                  />
                </Route>
                <Route element={<Protected roles={["ADMIN"]} />}>
                  <Route path="/felhasznalok" element={<UsersPage />} />
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
