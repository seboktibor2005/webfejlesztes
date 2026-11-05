import { useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, History, X } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { formatDate, reservationLabels } from "../reservations/format";
import { LoanPanel } from "../loans/LoanPanel";

const eventLabels = {
  RESERVATION_CREATED: "Igény benyújtva",
  RESERVATION_APPROVED: "Igény jóváhagyva",
  RESERVATION_REJECTED: "Igény elutasítva",
  RESERVATION_CANCELLED: "Foglalás visszavonva",
  LOAN_ISSUED: "Eszköz átadva",
  LOAN_RETURNED: "Eszköz visszavéve",
  MAINTENANCE_OPENED: "Karbantartási zárolás létrehozva",
  MAINTENANCE_CLOSED: "Karbantartás lezárva",
};
export function ReservationPage() {
  const { id } = useParams();
  const { token, user } = useAuth();
  const location = useLocation();
  const client = useQueryClient();
  const [action, setAction] = useState(null);
  const [reason, setReason] = useState("");
  const [notice, setNotice] = useState(
    location.state?.created
      ? "Az igényed megérkezett. Az eszközfelelős döntése után lesz biztosított az időszak."
      : "",
  );
  const [eventPage, setEventPage] = useState(0);
  const detail = useQuery({
    queryKey: ["reservation", id],
    refetchInterval: 30_000,
    queryFn: ({ signal }) => api(`/reservations/${id}`, { token, signal }),
  });
  const events = useQuery({
    queryKey: ["reservation-events", id, eventPage],
    enabled: detail.isSuccess,
    queryFn: ({ signal }) =>
      api(`/reservations/${id}/events?page=${eventPage}&size=10`, {
        token,
        signal,
      }),
  });
  const mutation = useMutation({
    mutationFn: (command) => {
      const management =
        command !== "cancel" || detail.data.requester.id !== user.id;
      return api(
        `${management ? "/management" : ""}/reservations/${id}/${command}`,
        {
          token,
          method: "POST",
          body: {
            version: detail.data.version,
            ...(reason.trim() ? { reason: reason.trim() } : {}),
          },
        },
      );
    },
    onSuccess: (reservation) => {
      client.setQueryData(["reservation", id], reservation);
      client.invalidateQueries({ queryKey: ["reservations"] });
      client.invalidateQueries({ queryKey: ["reservation-events", id] });
      setNotice(
        "A művelet sikerült. Az állapot és az eseménytörténet frissült.",
      );
      setAction(null);
      setReason("");
    },
    onError: (error) => {
      if (error.status === 409) {
        client.invalidateQueries({ queryKey: ["reservation", id] });
        client.invalidateQueries({ queryKey: ["reservations"] });
        client.invalidateQueries({ queryKey: ["reservation-events", id] });
      }
    },
  });
  const manager = user.role !== "USER";
  if (detail.isPending)
    return (
      <main className="page">
        <p role="status">Foglalás betöltése…</p>
      </main>
    );
  if (detail.isError)
    return (
      <main className="page">
        <ErrorState error={detail.error} retry={detail.refetch} />
      </main>
    );
  const reservation = detail.data;
  const own = reservation.requester.id === user.id;
  const editable = ["REQUESTED", "APPROVED"].includes(reservation.status);
  const cancelAllowed =
    editable &&
    !reservation.loan &&
    ((own && Date.parse(reservation.startsAt) > Date.now()) ||
      (manager && !own));
  const reasonRequired = action === "reject" || (action === "cancel" && !own);
  const titles = {
    approve: "Jóváhagyod ezt az igényt?",
    reject: "Elutasítod ezt az igényt?",
    cancel: "Visszavonod ezt a foglalást?",
  };
  function choose(command) {
    mutation.reset();
    setNotice("");
    setReason("");
    setAction(command);
  }
  return (
    <main className="page reservation-page">
      <Link className="back-link" to={manager ? "/elbiralas" : "/foglalasok"}>
        <ArrowLeft size={17} />
        {manager ? "Vissza az elbíráláshoz" : "Vissza a foglalásaimhoz"}
      </Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">FOGLALÁSI IGÉNY · #{reservation.id}</span>
          <h1>{reservation.equipment.name}</h1>
          <p>
            {reservation.equipment.inventoryCode} ·{" "}
            {reservation.requester.displayName}
          </p>
        </div>
        <span
          className={`reservation-status status-${reservation.status.toLowerCase()}`}
        >
          {reservationLabels[reservation.status]}
        </span>
      </div>
      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}
      <LoanPanel reservation={reservation} />
      <div className="reservation-detail-grid">
        <section className="reservation-summary-panel">
          <h2>Az igény részletei</h2>
          <dl className="detail-facts">
            <div>
              <dt>Átvétel</dt>
              <dd>{formatDate(reservation.startsAt)}</dd>
            </div>
            <div>
              <dt>Visszahozás</dt>
              <dd>{formatDate(reservation.endsAt)}</dd>
            </div>
            <div>
              <dt>Igénylő</dt>
              <dd>{reservation.requester.displayName}</dd>
            </div>
            {reservation.decidedBy && (
              <div>
                <dt>Elbíráló</dt>
                <dd>{reservation.decidedBy.displayName}</dd>
              </div>
            )}
          </dl>
          <h3>Felhasználás célja</h3>
          <p className="reservation-purpose">{reservation.purpose}</p>
          {reservation.decisionReason && (
            <>
              <h3>Elutasítás indoka</h3>
              <p className="reservation-purpose">
                {reservation.decisionReason}
              </p>
            </>
          )}
          {reservation.status === "REQUESTED" && (
            <p className="detail-note">
              Ez még függő igény. Az időszakot a jóváhagyás biztosítja.
            </p>
          )}
          <div className="reservation-actions">
            {manager && reservation.status === "REQUESTED" && !own && (
              <button
                className="button primary"
                onClick={() => choose("approve")}
                disabled={mutation.isPending}
              >
                <Check size={17} />
                Jóváhagyás
              </button>
            )}
            {manager && reservation.status === "REQUESTED" && (
              <button
                className="button secondary"
                onClick={() => choose("reject")}
                disabled={mutation.isPending}
              >
                <X size={17} />
                Elutasítás
              </button>
            )}
            {cancelAllowed && (
              <button
                className="button secondary"
                onClick={() => choose("cancel")}
                disabled={mutation.isPending}
              >
                Visszavonás
              </button>
            )}
          </div>
          {manager && own && reservation.status === "REQUESTED" && (
            <p className="input-help">
              Saját igényedről másik eszközfelelős vagy adminisztrátor hozhat
              jóváhagyó döntést.
            </p>
          )}
          {action && (
            <section
              className="decision-panel"
              aria-labelledby="decision-title"
            >
              <h3 id="decision-title">{titles[action]}</h3>
              {action === "approve" && (
                <p>
                  A rendszer a véglegesítéskor ismét ellenőrzi az eszköz
                  foglalhatóságát.
                </p>
              )}
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  mutation.mutate(action);
                }}
              >
                <fieldset disabled={mutation.isPending}>
                  {action !== "approve" && (
                    <label>
                      Indok{!reasonRequired && " (nem kötelező)"}
                      <textarea
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        required={reasonRequired}
                        minLength={reasonRequired ? 5 : undefined}
                        maxLength={500}
                        rows={3}
                      />
                    </label>
                  )}
                  {mutation.isError && (
                    <div className="form-error" role="alert">
                      {mutation.error.message}
                    </div>
                  )}
                  <div className="reservation-actions">
                    <button
                      className="button primary"
                      type="submit"
                      disabled={mutation.isPending}
                    >
                      {mutation.isPending ? "Mentés…" : "Döntés megerősítése"}
                    </button>
                    <button
                      className="button secondary"
                      type="button"
                      onClick={() => {
                        setAction(null);
                        mutation.reset();
                      }}
                    >
                      Mégse
                    </button>
                  </div>
                </fieldset>
              </form>
            </section>
          )}
        </section>
        <section className="reservation-history">
          <h2>
            <History size={20} />
            Eseménytörténet
          </h2>
          {events.isPending ? (
            <p role="status">Események betöltése…</p>
          ) : events.isError ? (
            <ErrorState error={events.error} retry={events.refetch} />
          ) : (
            <>
              <ol className="event-timeline">
                {events.data.content.map((event) => (
                  <li key={event.id}>
                    <strong>
                      {eventLabels[event.eventType] || event.eventType}
                    </strong>
                    <span>
                      {event.actor?.displayName || "Rendszer"} ·{" "}
                      {formatDate(event.occurredAt)}
                    </span>
                    {event.note && <p>{event.note}</p>}
                  </li>
                ))}
              </ol>
              {events.data.page.totalPages > 1 && (
                <div className="pagination">
                  <span>{eventPage + 1}. oldal</span>
                  <div>
                    <button
                      className="button secondary"
                      disabled={eventPage === 0}
                      onClick={() => setEventPage(eventPage - 1)}
                    >
                      Előző
                    </button>
                    <button
                      className="button secondary"
                      disabled={eventPage + 1 >= events.data.page.totalPages}
                      onClick={() => setEventPage(eventPage + 1)}
                    >
                      Következő
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  );
}
