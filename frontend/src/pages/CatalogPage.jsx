import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowDownAZ,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Plus,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { EmptyState, ErrorState, LoadingCards } from "../components/Feedback";
import { CatalogPeriodFilter } from "../availability/CatalogPeriodFilter";

export function CatalogPage({ management = false }) {
  const { token } = useAuth();
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
          <h1>{management ? "Eszközállomány" : "Eszközkatalógus"}</h1>
        </div>
        {management && (
          <Link className="button primary" to="/kezeles/uj">
            <Plus size={16} />
            Új eszköz felvétele
          </Link>
        )}
      </div>
      <section className="catalog-section" aria-label="Eszközkereső">
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
            Minden kategória
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
        {!management && (
          <CatalogPeriodFilter
            startsAt={startsAt}
            endsAt={endsAt}
            onApply={applyPeriod}
            onClear={() => applyPeriod("", "")}
          />
        )}
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
        <div className="catalog-results-heading">
          <h2>Találatok</h2>
          {equipment.data && !equipment.isError && (
            <span role="status">
              {equipment.data.page.totalElements} eszköz
            </span>
          )}
        </div>
        {equipment.isPending ? (
          <LoadingCards />
        ) : equipment.isError ? (
          <ErrorState error={equipment.error} retry={equipment.refetch} />
        ) : equipment.data.content.length === 0 ? (
          <EmptyState reset={reset} />
        ) : (
          <div className="equipment-list">
            <div
              className="equipment-list-head equipment-row"
              aria-hidden="true"
            >
              <span>Eszköz</span>
              <span>Leltári szám</span>
              <span>Tárolási hely</span>
            </div>
            {equipment.data.content.map((item) => (
              <article className="equipment-row" key={item.id}>
                <div className="equipment-row-main">
                  <h3>{item.name}</h3>
                  {item.description && <p>{item.description}</p>}
                  {item.lifecycle === "RETIRED" ? (
                    <span className="reservation-status status-cancelled">
                      Kivonva
                    </span>
                  ) : startsAt && endsAt ? (
                    <span className="reservation-status status-approved">
                      Az időszakra igényelhető
                    </span>
                  ) : null}
                </div>
                <div className="equipment-row-code">
                  <code>{item.inventoryCode}</code>
                  <span>{item.category.name}</span>
                </div>
                <div className="equipment-row-location">
                  {item.location.name}
                </div>
                {(management || item.lifecycle === "ACTIVE") && (
                  <Link
                    className="equipment-row-link"
                    to={
                      management
                        ? `/kezeles/${item.id}`
                        : `/eszkozok/${item.id}${periodQuery.size ? `?${periodQuery}` : ""}`
                    }
                    aria-label={`${item.name} ${management ? "szerkesztése" : "részletei"}`}
                  >
                    {management ? "Szerkesztés" : "Adatlap"}
                    <ArrowRight size={16} aria-hidden="true" />
                  </Link>
                )}
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
    </main>
  );
}
