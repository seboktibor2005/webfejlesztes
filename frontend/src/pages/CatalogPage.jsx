import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowDownAZ,
  ArrowRight,
  Boxes,
  ChevronLeft,
  ChevronRight,
  MapPin,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { EquipmentArt } from "../components/EquipmentArt";
import { EmptyState, ErrorState, LoadingCards } from "../components/Feedback";
import { CatalogPeriodFilter } from "../availability/CatalogPeriodFilter";

export function CatalogPage({ management = false }) {
  const { token, user } = useAuth();
  const [params, setParams] = useSearchParams();
  const search = params.get("q") || "";
  const [debouncedSearch, setDebouncedSearch] = useState(search);
  const page = Math.max(0, Number(params.get("page")) || 0);
  const category = params.get("categoryId") || "";
  const location = params.get("locationId") || "";
  const sort = params.get("sort") || "name,asc";
  const startsAt = management ? "" : params.get("startsAt") || "";
  const endsAt = management ? "" : params.get("endsAt") || "";
  const periodQuery = new URLSearchParams();
  if (startsAt) periodQuery.set("startsAt", startsAt);
  if (endsAt) periodQuery.set("endsAt", endsAt);
  function applyPeriod(start, end) {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.delete("page");
        if (start) next.set("startsAt", start);
        else next.delete("startsAt");
        if (end) next.set("endsAt", end);
        else next.delete("endsAt");
        return next;
      },
      { replace: true },
    );
  }
  function filter(name, value) {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (value) next.set(name, value);
        else next.delete(name);
        if (name !== "page") next.delete("page");
        return next;
      },
      { replace: true },
    );
  }
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: ({ signal }) => api("/categories", { token, signal }),
  });
  const locations = useQuery({
    queryKey: ["locations"],
    queryFn: ({ signal }) => api("/locations", { token, signal }),
  });
  const query = new URLSearchParams({ page: String(page), size: "6", sort });
  if (debouncedSearch) query.set("q", debouncedSearch);
  if (category) query.set("categoryId", category);
  if (location) query.set("locationId", location);
  if (startsAt) query.set("startsAt", startsAt);
  if (endsAt) query.set("endsAt", endsAt);
  const equipment = useQuery({
    queryKey: ["equipment", management, query.toString()],
    queryFn: ({ signal }) =>
      api(`${management ? "/management" : ""}/equipment?${query}`, {
        token,
        signal,
      }),
    refetchInterval: startsAt || endsAt ? 30_000 : false,
  });
  const hasFilters = search || category || location || startsAt || endsAt;
  function reset() {
    setParams({}, { replace: true });
  }
  return (
    <main className="page catalog-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">
            {management
              ? "ESZKÖZFELELŐSI NÉZET"
              : "KÖZÖS ESZKÖZÖK, ÚJ LEHETŐSÉGEK"}
          </span>
          <h1>
            {management
              ? "Az eszközállomány."
              : "Mi kell a következő ötletedhez?"}
          </h1>
          <p>
            {management
              ? "Az aktív és kivont eszközök teljes, intézményi nyilvántartása."
              : `Szia, ${user.displayName.split(" ").at(-1)}! Fedezd fel az egyetemi eszköztárat.`}
          </p>
        </div>
        <span className="heading-symbol">
          <Boxes size={30} strokeWidth={1.4} />
        </span>
      </div>
      {management && (
        <div className="management-toolbar">
          <Link className="button primary" to="/kezeles/uj">
            Új eszköz felvétele
          </Link>
        </div>
      )}
      <section className="catalog-banner">
        <div>
          <span className="small-label">A JÓ ÖTLETEKNEK HELYET ADUNK</span>
          <h2>
            Kevesebb akadály.
            <br />
            <em>Több alkotás.</em>
          </h2>
          <p>
            Fedezz fel új eszközöket,
            <br />
            és hozd közelebb a terveidet.
          </p>
        </div>
        <div className="banner-art">
          <EquipmentArt code="FOTO-001" large />
        </div>
        <span className="banner-index">01 / ESZKÖZTÁR</span>
      </section>
      <section className="catalog-section" aria-label="Eszközkereső">
        <div className="section-top">
          <div>
            <h2>
              {management ? "Teljes leltár" : "Fedezd fel az eszközöket"}
              <span className="count-badge">
                {equipment.data?.page.totalElements ?? "…"}
              </span>
            </h2>
            <p>
              {management
                ? "A kivont eszközök csak ebben a kezelői nézetben láthatók."
                : "Válassz kategóriát, vagy keress rá egy konkrét eszközre."}
            </p>
          </div>
          <span className="catalog-caption">
            <span className="live-dot" />
            Egyetemi eszköztár
          </span>
        </div>
        {!management && (
          <CatalogPeriodFilter
            startsAt={startsAt}
            endsAt={endsAt}
            onApply={applyPeriod}
            onClear={() => applyPeriod("", "")}
          />
        )}
        <div className="filter-bar">
          <label className="search-input">
            <Search size={19} />
            <input
              aria-label="Eszköz keresése"
              placeholder="Keresés névre vagy leltári számra…"
              value={search}
              onChange={(event) => filter("q", event.target.value)}
              maxLength={120}
            />
            {equipment.isFetching && !equipment.isPending && (
              <span
                className="search-spinner"
                aria-label="Keresés folyamatban"
              />
            )}
          </label>
          <label className="select-wrap">
            <SlidersHorizontal size={16} />
            <select
              aria-label="Tárolási hely"
              value={location}
              onChange={(event) => filter("locationId", event.target.value)}
            >
              <option value="">Minden helyszín</option>
              {locations.data?.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label className="select-wrap sort-select">
            <ArrowDownAZ size={17} />
            <select
              aria-label="Rendezés"
              value={sort}
              onChange={(event) => filter("sort", event.target.value)}
            >
              <option value="name,asc">Név: A–Z</option>
              <option value="name,desc">Név: Z–A</option>
              <option value="inventoryCode,asc">Leltári szám</option>
            </select>
          </label>
        </div>
        <div className="category-tabs" aria-label="Eszközkategóriák">
          <button
            className={!category ? "selected" : ""}
            onClick={() => filter("categoryId", "")}
          >
            Minden eszköz
          </button>
          {categories.data?.map((item) => (
            <button
              key={item.id}
              className={category === String(item.id) ? "selected" : ""}
              onClick={() => filter("categoryId", String(item.id))}
            >
              {item.name}
            </button>
          ))}
          {hasFilters && (
            <button className="clear-filter" onClick={reset}>
              Szűrők törlése
            </button>
          )}
        </div>
        {(categories.isError || locations.isError) && (
          <p className="form-error" role="alert">
            Néhány szűrő nem tölthető be.{" "}
            <button
              onClick={() => {
                categories.refetch();
                locations.refetch();
              }}
            >
              Újrapróbálás
            </button>
          </p>
        )}
        {equipment.isPending ? (
          <LoadingCards />
        ) : equipment.isError ? (
          <ErrorState error={equipment.error} retry={equipment.refetch} />
        ) : equipment.data.content.length === 0 ? (
          <EmptyState reset={reset} />
        ) : (
          <div className="equipment-grid">
            {equipment.data.content.map((item) => (
              <article className="equipment-card" key={item.id}>
                <div className="card-art-wrap">
                  <EquipmentArt code={item.inventoryCode} />
                  <span
                    className={`lifecycle-badge ${item.lifecycle === "RETIRED" ? "retired" : ""}`}
                  >
                    <span />
                    {item.lifecycle === "ACTIVE"
                      ? !management && startsAt && endsAt
                        ? "Az időszakra igényelhető"
                        : "Az eszköztárban"
                      : "Kivonva"}
                  </span>
                </div>
                <div className="card-body">
                  <span className="card-category">{item.category.name}</span>
                  <h3>{item.name}</h3>
                  <p className="card-description">{item.description}</p>
                  <div className="card-footer">
                    <span>
                      <MapPin size={14} />
                      {item.location.name}
                    </span>
                    {management ? (
                      <Link
                        to={`/kezeles/${item.id}`}
                        aria-label={`${item.name} szerkesztése`}
                      >
                        <ArrowRight size={19} />
                      </Link>
                    ) : item.lifecycle === "ACTIVE" ? (
                      <Link
                        to={`/eszkozok/${item.id}${periodQuery.size ? `?${periodQuery}` : ""}`}
                        aria-label={`${item.name} részletei`}
                      >
                        <ArrowRight size={19} />
                      </Link>
                    ) : (
                      <span className="retired-label">Archivált</span>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
        {equipment.data && equipment.data.page.totalPages > 1 && (
          <div className="pagination">
            <span>
              {page + 1}. oldal / {equipment.data.page.totalPages}
            </span>
            <div>
              <button
                className="button secondary"
                disabled={page === 0}
                onClick={() => filter("page", String(page - 1))}
              >
                <ChevronLeft size={16} />
                Előző
              </button>
              <button
                className="button secondary"
                disabled={page + 1 >= equipment.data.page.totalPages}
                onClick={() => filter("page", String(page + 1))}
              >
                Következő
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </section>
      <footer className="page-footer">
        <span>EszközPont · Közösen több lehetőség.</span>
        <span>Az eszközök egyetemi projektekhez állnak rendelkezésre.</span>
      </footer>
    </main>
  );
}
