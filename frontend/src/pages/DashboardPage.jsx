import { useQuery } from "@tanstack/react-query";
import { ArrowRight, RotateCw } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { formatDate } from "../reservations/format";

const dateFormatter = new Intl.DateTimeFormat("hu-HU", {
  timeZone: "UTC",
  year: "numeric",
  month: "short",
  day: "numeric",
});

function Appointment({ title, appointment, empty, overdue = false }) {
  return (
    <li className="dashboard-appointment">
      <div>
        <h3>{title}</h3>
        {appointment ? (
          <>
            <strong>{appointment.equipmentName}</strong>
            <p>
              <code>{appointment.inventoryCode}</code>
              <span> · </span>
              <time dateTime={appointment.at}>
                {formatDate(appointment.at)}
              </time>
            </p>
            {overdue && (
              <span className="reservation-status status-rejected">
                Késésben
              </span>
            )}
          </>
        ) : (
          <p>{empty}</p>
        )}
      </div>
      {appointment && (
        <Link
          className="dashboard-link"
          to={`/foglalasok/${appointment.reservationId}`}
          aria-label={`${title} részletei`}
        >
          Részletek <ArrowRight size={15} aria-hidden="true" />
        </Link>
      )}
    </li>
  );
}

function PersonalOverview({ data }) {
  const overdue =
    data.nextReturn &&
    Date.parse(data.nextReturn.at) < Date.parse(data.checkedAt);
  return (
    <>
      <dl className="dashboard-personal-counts">
        <div>
          <dt>Elbírálásra váró igény</dt>
          <dd>{data.pendingCount}</dd>
        </div>
        <div>
          <dt>Nálad lévő eszköz</dt>
          <dd>{data.activeLoanCount}</dd>
        </div>
      </dl>
      <section
        className="dashboard-panel"
        aria-labelledby="dashboard-appointments"
      >
        <h2 id="dashboard-appointments">Következő teendők</h2>
        <ul className="dashboard-appointments">
          <Appointment
            title="Következő átvétel"
            appointment={data.nextPickup}
            empty="Nincs soron következő átvételed."
          />
          <Appointment
            title="Legkorábbi visszahozás"
            appointment={data.nextReturn}
            empty="Jelenleg nincs nálad kölcsönzött eszköz."
            overdue={overdue}
          />
        </ul>
      </section>
      <div className="dashboard-actions">
        <Link className="button primary" to="/eszkozok">
          Eszközök keresése
        </Link>
        <Link className="button secondary" to="/foglalasok">
          Saját foglalások
        </Link>
      </div>
    </>
  );
}

function ManagementOverview({ data, admin }) {
  const tasks = [
    ["Elbírálásra váró igény", data.pendingCount],
    ["Ma kezdődő, átadásra váró foglalás", data.todayPickupCount],
    ["Ma esedékes visszahozás", data.todayReturnCount],
    ["Késésben lévő kölcsönzés", data.overdueLoanCount],
    ["Nyitott karbantartás", data.maintenanceCount],
  ];
  const maxCount = data.loansByCategory.reduce(
    (max, { count }) => Math.max(max, count),
    1,
  );
  return (
    <>
      <div className="dashboard-grid">
        <section className="dashboard-panel" aria-labelledby="dashboard-tasks">
          <h2 id="dashboard-tasks">Eszköztári teendők</h2>
          <p className="dashboard-panel-note">
            {dateFormatter.format(new Date(data.date))} · budapesti idő szerint
          </p>
          <dl className="dashboard-task-list">
            {tasks.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          <div className="dashboard-actions">
            <Link className="text-link" to="/elbiralas">
              Elbírálási lista
            </Link>
            <Link className="text-link" to="/kolcsonzesek">
              Kölcsönzési lista
            </Link>
            <Link className="text-link" to="/karbantartasok">
              Karbantartások
            </Link>
          </div>
        </section>
        <section
          className="dashboard-panel"
          aria-labelledby="dashboard-history"
        >
          <h2 id="dashboard-history">Átadások kategóriánként</h2>
          <p className="dashboard-panel-note">
            Utolsó 30 naptári nap ·{" "}
            {dateFormatter.format(new Date(data.historyFrom))} –{" "}
            {dateFormatter.format(new Date(data.date))}
          </p>
          {data.loansByCategory.length ? (
            <ul className="dashboard-bars">
              {data.loansByCategory.map(
                ({ categoryId, categoryName, count }) => (
                  <li key={categoryId}>
                    <div>
                      <span>{categoryName}</span>
                      <strong>{count} átadás</strong>
                    </div>
                    <div className="dashboard-track" aria-hidden="true">
                      <span style={{ width: `${(count / maxCount) * 100}%` }} />
                    </div>
                  </li>
                ),
              )}
            </ul>
          ) : (
            <p className="dashboard-panel-note">
              Ebben az időszakban nem történt átadás.
            </p>
          )}
        </section>
      </div>
      {admin && (
        <section className="dashboard-admin" aria-labelledby="dashboard-admin">
          <h2 id="dashboard-admin">Adminisztráció</h2>
          <div className="dashboard-actions">
            <Link className="text-link" to="/felhasznalok">
              Felhasználók
            </Link>
            <Link className="text-link" to="/torzsadatok">
              Törzsadatok
            </Link>
          </div>
        </section>
      )}
    </>
  );
}

export function DashboardPage() {
  const { user, token } = useAuth();
  const management = user.role === "MANAGER" || user.role === "ADMIN";
  const result = useQuery({
    queryKey: ["dashboard", user.id, user.role],
    queryFn: ({ signal }) =>
      api(management ? "/management/dashboard" : "/dashboard/me", {
        token,
        signal,
      }),
    staleTime: 0,
    refetchInterval: 30_000,
  });

  return (
    <main className="page dashboard-page">
      <header className="page-heading">
        <div>
          <h1>Áttekintés</h1>
        </div>
        <button
          className="button secondary"
          onClick={() => result.refetch()}
          disabled={result.isFetching}
        >
          <RotateCw size={15} aria-hidden="true" /> Frissítés
        </button>
      </header>
      {result.isPending ? (
        <p role="status">Az áttekintés betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <>
          {management ? (
            <ManagementOverview
              data={result.data}
              admin={user.role === "ADMIN"}
            />
          ) : (
            <PersonalOverview data={result.data} />
          )}
          <p className="dashboard-checked">
            Frissítve {formatDate(result.data.checkedAt)}
          </p>
        </>
      )}
    </main>
  );
}
