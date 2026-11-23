import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { ErrorState } from "../components/Feedback";
import { formatDate, reservationLabels } from "../reservations/format";

export function ReservationsPage({ management = false }) {
  const { token } = useAuth();
  const [status, setStatus] = useState(management ? "REQUESTED" : "");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("createdAt,desc");
  const params = new URLSearchParams({ page: String(page), size: "12", sort });
  if (status) params.set("status", status);
  const result = useQuery({
    queryKey: ["reservations", management, params.toString()],
    refetchInterval: 30_000,
    queryFn: ({ signal }) =>
      api(`${management ? "/management" : "/me"}/reservations?${params}`, {
        token,
        signal,
      }),
  });
  return (
    <main className="page reservation-page">
      <div className="page-heading">
        <div>
          <h1>{management ? "Igények elbírálása" : "Saját foglalások"}</h1>
        </div>
      </div>
      <div className="reservation-filters">
        <label>
          Állapot
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Minden állapot</option>
            {Object.entries(reservationLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Rendezés
          <select
            value={sort}
            onChange={(event) => {
              setSort(event.target.value);
              setPage(0);
            }}
          >
            <option value="createdAt,desc">Legújabb igények</option>
            <option value="startsAt,asc">Legkorábbi átvétel</option>
            <option value="startsAt,desc">Legkésőbbi átvétel</option>
          </select>
        </label>
      </div>
      {result.isPending ? (
        <p role="status">Foglalások betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : result.data.content.length === 0 ? (
        <section className="state-panel">
          <CalendarDays size={34} />
          <h2>
            {status
              ? "Ebben az állapotban nincs igény."
              : "Még nem adtál be igényt."}
          </h2>
          <p>
            {management
              ? "Az új igények itt jelennek majd meg."
              : "Nézz körül a katalógusban, és válassz eszközt a projektedhez."}
          </p>
          <Link className="button secondary" to="/eszkozok">
            Eszközkatalógus
          </Link>
        </section>
      ) : (
        <>
          <div className="table-wrap">
            <table className="reservation-table">
              <caption>
                {result.data.page.totalElements} foglalási igény
              </caption>
              <thead>
                <tr>
                  <th>Eszköz</th>
                  {management && <th>Igénylő</th>}
                  <th>Időszak</th>
                  <th>Állapot</th>
                  <th>
                    <span className="sr-only">Részletek</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((reservation) => (
                  <tr key={reservation.id}>
                    <td>
                      <strong>{reservation.equipment.name}</strong>
                      <small className="table-secondary">
                        {reservation.equipment.inventoryCode} · #
                        {reservation.id}
                      </small>
                    </td>
                    {management && <td>{reservation.requester.displayName}</td>}
                    <td>
                      <span>{formatDate(reservation.startsAt)}</span>
                      <small className="table-secondary">
                        {formatDate(reservation.endsAt)}
                      </small>
                    </td>
                    <td>
                      <span
                        className={`reservation-status status-${reservation.status.toLowerCase()}`}
                      >
                        {reservationLabels[reservation.status]}
                      </span>
                      {reservation.hasOpenLoan && (
                        <span
                          className={`reservation-status ${reservation.overdue ? "status-rejected" : "status-approved"}`}
                        >
                          {reservation.overdue ? "Késésben" : "Átadva"}
                        </span>
                      )}
                      {reservation.atRisk && (
                        <span className="reservation-status status-requested">
                          Átadás veszélyeztetett
                        </span>
                      )}
                    </td>
                    <td>
                      <Link
                        className="table-detail-link"
                        to={`/foglalasok/${reservation.id}`}
                        aria-label={`Foglalás #${reservation.id} részletei`}
                      >
                        <ArrowRight size={19} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {result.data.page.totalPages > 1 && (
            <div className="pagination">
              <span>
                {page + 1}. oldal / {result.data.page.totalPages}
              </span>
              <div>
                <button
                  className="button secondary"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  <ChevronLeft size={16} />
                  Előző
                </button>
                <button
                  className="button secondary"
                  disabled={page + 1 >= result.data.page.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Következő
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </main>
  );
}
