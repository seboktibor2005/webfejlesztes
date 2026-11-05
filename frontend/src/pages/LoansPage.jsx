import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, PackageCheck } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { formatDate } from "../reservations/format";
import { LoanStatus } from "../loans/LoanPanel";

export function LoansPage() {
  const { token } = useAuth();
  const [filter, setFilter] = useState("open");
  const [page, setPage] = useState(0);
  const params = new URLSearchParams({ page: String(page), size: "12" });
  if (filter === "open" || filter === "overdue") params.set("open", "true");
  if (filter === "overdue") params.set("overdue", "true");
  if (filter === "closed") params.set("open", "false");
  const result = useQuery({
    queryKey: ["loans", params.toString()],
    refetchInterval: 30_000,
    queryFn: ({ signal }) =>
      api(`/management/loans?${params}`, { token, signal }),
  });
  return (
    <main className="page reservation-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">AMI MOST MOZGÁSBAN VAN</span>
          <h1>Kölcsönzések.</h1>
          <p>Kövesd az átadott eszközöket és a visszahozási határidőket.</p>
        </div>
        <span className="heading-symbol">
          <PackageCheck size={28} />
        </span>
      </div>
      <div className="reservation-filters">
        <label>
          Kölcsönzések szűrése
          <select
            value={filter}
            onChange={(event) => {
              setFilter(event.target.value);
              setPage(0);
            }}
          >
            <option value="open">Nyitott kölcsönzések</option>
            <option value="overdue">Csak késésben</option>
            <option value="closed">Lezárt kölcsönzések</option>
            <option value="all">Minden kölcsönzés</option>
          </select>
        </label>
      </div>
      {result.isPending ? (
        <p role="status">Kölcsönzések betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : !result.data.content.length ? (
        <section className="state-panel">
          <PackageCheck size={34} />
          <h2>Nincs ilyen kölcsönzés.</h2>
          <p>
            Az átadott eszközök a kiválasztott feltétel szerint jelennek meg
            itt.
          </p>
          <Link className="button secondary" to="/elbiralas">
            Igények elbírálása
          </Link>
        </section>
      ) : (
        <>
          <div className="table-wrap">
            <table className="reservation-table">
              <caption>
                {result.data.page.totalElements} kölcsönzés · legkorábbi
                határidő elöl
              </caption>
              <thead>
                <tr>
                  <th>Eszköz</th>
                  <th>Igénylő</th>
                  <th>Határidő</th>
                  <th>Állapot</th>
                  <th>
                    <span className="sr-only">Részletek</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((loan) => (
                  <tr key={loan.id}>
                    <td>
                      <strong>{loan.equipment.name}</strong>
                      <small className="table-secondary">
                        {loan.equipment.inventoryCode} · #{loan.id}
                      </small>
                    </td>
                    <td>{loan.requester.displayName}</td>
                    <td>{formatDate(loan.dueAt)}</td>
                    <td>
                      <LoanStatus loan={loan} />
                    </td>
                    <td>
                      <Link
                        className="table-detail-link"
                        to={`/foglalasok/${loan.reservationId}`}
                        aria-label={`Kölcsönzés #${loan.id} részletei`}
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
                  disabled={!page}
                  onClick={() => setPage(page - 1)}
                >
                  Előző
                </button>
                <button
                  className="button secondary"
                  disabled={page + 1 >= result.data.page.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Következő
                </button>
              </div>
            </div>
          )}
        </>
      )}
      <p className="input-help loan-refresh-note">
        A lista 30 másodpercenként frissül. A késést a szerver számítja; a
        lejárt határidő nem zárja le a kölcsönzést.
      </p>
    </main>
  );
}
