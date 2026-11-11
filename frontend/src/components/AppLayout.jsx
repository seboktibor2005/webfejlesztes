import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useMatch } from "react-router-dom";
import {
  Boxes,
  ClipboardList,
  Users,
  LogOut,
  Menu,
  X,
  CalendarDays,
  ListChecks,
  PackageCheck,
  Wrench,
  Tags,
  LayoutList,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { Brand } from "./Brand";

export const roleLabels = {
  USER: "Hallgató",
  MANAGER: "Eszközfelelős",
  ADMIN: "Adminisztrátor",
};
export function AppLayout() {
  const { user, logout } = useAuth();
  const reservationDetail = useMatch("/foglalasok/:id");
  const reviewList = useMatch("/elbiralas");
  const reviewingReservation = user.role !== "USER" && reservationDetail !== null;
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  useEffect(() => {
    if (!open) return;
    const closeOnEscape = (event) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [open]);
  async function signOut() {
    setPending(true);
    setError("");
    try {
      await logout();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="app-layout">
      <header className="mobile-header">
        <Brand />
        <button
          className="icon-button"
          aria-label={open ? "Menü bezárása" : "Menü megnyitása"}
          aria-expanded={open}
          aria-controls="main-sidebar"
          onClick={() => setOpen(!open)}
        >
          {open ? <X /> : <Menu />}
        </button>
      </header>
      {open && (
        <button
          className="sidebar-overlay"
          aria-label="Menü bezárása"
          onClick={() => setOpen(false)}
        />
      )}
      <aside id="main-sidebar" className={`sidebar ${open ? "is-open" : ""}`}>
        <Brand />
        <div className="workspace-label">Egyetemi eszköztár</div>
        <nav aria-label="Fő navigáció">
          <NavLink to="/attekintes" onClick={() => setOpen(false)}>
            <LayoutList size={20} />
            Áttekintés
          </NavLink>
          <NavLink to="/eszkozok" onClick={() => setOpen(false)}>
            <Boxes size={20} />
            Eszközkatalógus
          </NavLink>
          <NavLink
            to="/foglalasok"
            end={reviewingReservation}
            onClick={() => setOpen(false)}
          >
            <CalendarDays size={20} />
            Saját foglalások
          </NavLink>
          {user.role !== "USER" && <div className="nav-label">Kezelés</div>}
          {user.role !== "USER" && (
            <Link
              to="/elbiralas"
              className={
                reviewList || reviewingReservation ? "active" : undefined
              }
              aria-current={
                reviewList ? "page" : reviewingReservation ? "location" : undefined
              }
              onClick={() => setOpen(false)}
            >
              <ListChecks size={20} />
              Igények elbírálása
            </Link>
          )}
          {user.role !== "USER" && (
            <NavLink to="/kezeles" onClick={() => setOpen(false)}>
              <ClipboardList size={20} />
              Eszközállomány
            </NavLink>
          )}
          {user.role !== "USER" && (
            <NavLink to="/kolcsonzesek" onClick={() => setOpen(false)}>
              <PackageCheck size={20} />
              Kölcsönzések
            </NavLink>
          )}
          {user.role !== "USER" && (
            <NavLink to="/karbantartasok" onClick={() => setOpen(false)}>
              <Wrench size={20} />
              Karbantartások
            </NavLink>
          )}
          {user.role === "ADMIN" && <div className="nav-label">Adminisztráció</div>}
          {user.role === "ADMIN" && (
            <NavLink to="/felhasznalok" onClick={() => setOpen(false)}>
              <Users size={20} />
              Felhasználók
            </NavLink>
          )}
          {user.role === "ADMIN" && (
            <NavLink to="/torzsadatok" onClick={() => setOpen(false)}>
              <Tags size={20} />
              Törzsadatok
            </NavLink>
          )}
        </nav>
        <div className="sidebar-bottom">
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          <div className="user-card">
            <span className="avatar">
              {user.displayName
                .split(" ")
                .map((p) => p[0])
                .slice(0, 2)
                .join("")}
            </span>
            <div>
              <strong>{user.displayName}</strong>
              <small>{roleLabels[user.role]}</small>
            </div>
            <button
              className="icon-button"
              aria-label="Kijelentkezés"
              disabled={pending}
              onClick={signOut}
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>
      <div className="app-content">
        <Outlet />
      </div>
    </div>
  );
}
