import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PackageCheck, Undo2, Wrench } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import {
  formatDate,
  fromBudapestInput,
  toBudapestInput,
} from "../reservations/format";

export const conditionLabels = {
  GOOD: "Jó állapotú",
  WORN: "Használt, működőképes",
  DAMAGED: "Sérült",
};

export function LoanStatus({ loan }) {
  return (
    <span
      className={`reservation-status ${loan.overdue ? "status-rejected" : loan.returnedAt ? "status-completed" : "status-approved"}`}
    >
      {loan.overdue
        ? `Késésben · ${loan.overdueMinutes} perc`
        : loan.returnedAt
          ? loan.returnedLate
            ? "Késve visszahozva"
            : "Visszavéve"
          : "Átadva"}
    </span>
  );
}

export function LoanPanel({ reservation }) {
  const { token, user } = useAuth();
  const client = useQueryClient();
  const manager = user.role !== "USER";
  const loan = reservation.loan;
  const [mode, setMode] = useState(null);
  const [condition, setCondition] = useState("GOOD");
  const [note, setNote] = useState("");
  const [endsAt, setEndsAt] = useState(() =>
    toBudapestInput(new Date(Date.now() + 86_400_000)),
  );
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  const refresh = () =>
    Promise.all([
      client.invalidateQueries({
        queryKey: ["reservation", String(reservation.id)],
      }),
      client.invalidateQueries({
        queryKey: ["reservation-events", String(reservation.id)],
      }),
      client.invalidateQueries({ queryKey: ["reservations"] }),
      client.invalidateQueries({ queryKey: ["loans"] }),
      client.invalidateQueries({ queryKey: ["availability"] }),
      client.invalidateQueries({ queryKey: ["equipment"] }),
    ]);
  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === "checkout")
        return api(`/management/reservations/${reservation.id}/checkout`, {
          token,
          method: "POST",
          body: { version: reservation.version, issueCondition: condition },
        });
      if (mode === "return")
        return api(`/management/loans/${loan.id}/return`, {
          token,
          method: "POST",
          body: {
            condition,
            ...(note.trim() ? { note: note.trim() } : {}),
            ...(condition === "DAMAGED"
              ? { maintenanceEndsAt: fromBudapestInput(endsAt) }
              : {}),
          },
        });
      return api(`/management/maintenance/${loan.maintenance.id}/close`, {
        token,
        method: "POST",
        body: note.trim() ? { note: note.trim() } : {},
      });
    },
    onSuccess: async () => {
      await refresh();
      setNotice(
        mode === "checkout"
          ? "Az átadás rögzítve. Az eszköz most az igénylőnél van."
          : mode === "return"
            ? "A visszavétel rögzítve, a foglalás lezárult."
            : "A karbantartás lezárva. A rendszer újra ellenőrzi a foglalhatóságot.",
      );
      setMode(null);
      setNote("");
    },
    onError: async (error) => {
      if (error.status === 409) await refresh();
    },
  });
  const inWindow =
    Date.parse(reservation.startsAt) <= now &&
    now < Date.parse(reservation.endsAt);
  function choose(value) {
    mutation.reset();
    setMode(value);
    setCondition("GOOD");
    setNote("");
    setNotice("");
  }
  if (!loan && reservation.status !== "APPROVED") return null;
  return (
    <section className="loan-panel" aria-labelledby="loan-heading">
      <div className="loan-heading">
        <h2 id="loan-heading">
          <PackageCheck size={20} />
          Átadás és visszavétel
        </h2>
        {loan && <LoanStatus loan={loan} />}
      </div>
      {reservation.atRisk && (
        <div className="loan-warning" role="status">
          Az átadás veszélyeztetett: az eszköz késedelmes visszahozás vagy
          nyitott karbantartás miatt nem áll biztosan rendelkezésre. A
          jóváhagyás megmaradt; átadás előtt rendezni kell az akadályt.
        </div>
      )}
      {loan ? (
        <>
          <dl className="detail-facts">
            <div>
              <dt>Átadta</dt>
              <dd>{loan.issuedBy.displayName}</dd>
            </div>
            <div>
              <dt>Átadás</dt>
              <dd>{formatDate(loan.issuedAt)}</dd>
            </div>
            <div>
              <dt>Visszahozási határidő</dt>
              <dd>{formatDate(loan.dueAt)}</dd>
            </div>
            <div>
              <dt>Átadási állapot</dt>
              <dd>{conditionLabels[loan.issueCondition]}</dd>
            </div>
            {loan.returnedAt && (
              <>
                <div>
                  <dt>Visszavette</dt>
                  <dd>{loan.returnedBy.displayName}</dd>
                </div>
                <div>
                  <dt>Visszavétel</dt>
                  <dd>{formatDate(loan.returnedAt)}</dd>
                </div>
                <div>
                  <dt>Visszavételi állapot</dt>
                  <dd>{conditionLabels[loan.returnCondition]}</dd>
                </div>
              </>
            )}
          </dl>
          {(loan.overdue || loan.returnedLate) && (
            <p className="loan-warning">
              {loan.overdue
                ? "Az eszköz még nincs visszahozva."
                : "Az eszköz késve érkezett vissza."}{" "}
              Késés: {loan.overdueMinutes} megkezdett perc.
            </p>
          )}
          {loan.returnNote && (
            <>
              <h3>Visszavételi megjegyzés</h3>
              <p className="reservation-purpose">{loan.returnNote}</p>
            </>
          )}
          {loan.maintenance && (
            <div className="maintenance-note">
              <h3>
                <Wrench size={17} />
                {loan.maintenance.status === "OPEN"
                  ? "Karbantartási zárolás"
                  : "Lezárt karbantartás"}
              </h3>
              <p>{loan.maintenance.reason}</p>
              <p>Tervezett befejezés: {formatDate(loan.maintenance.endsAt)}</p>
              {loan.maintenance.status === "OPEN" && (
                <p>
                  A zárolás a tervezett időpont után is megmarad a kezelői
                  lezárásig.
                </p>
              )}
              {loan.maintenance.closedAt && (
                <p>Lezárva: {formatDate(loan.maintenance.closedAt)}</p>
              )}
            </div>
          )}
        </>
      ) : (
        <p className="input-help">
          Az eszköz még nincs átadva. Átadás a foglalás kezdetétől a végéig
          lehetséges, az aktuális akadályok ellenőrzésével.
        </p>
      )}
      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}
      {mutation.isError && (
        <div className="form-error" role="alert">
          {mutation.error.message}
        </div>
      )}
      {manager && (
        <div className="reservation-actions">
          {!loan && (
            <button
              className="button primary"
              disabled={!inWindow || mutation.isPending}
              onClick={() => choose("checkout")}
            >
              <PackageCheck size={17} />
              Eszköz átadása
            </button>
          )}
          {loan && !loan.returnedAt && (
            <button
              className="button primary"
              disabled={mutation.isPending}
              onClick={() => choose("return")}
            >
              <Undo2 size={17} />
              Eszköz visszavétele
            </button>
          )}
          {loan?.maintenance?.status === "OPEN" && (
            <button
              className="button secondary"
              disabled={mutation.isPending}
              onClick={() => choose("close")}
            >
              <Wrench size={17} />
              Karbantartás lezárása
            </button>
          )}
        </div>
      )}
      {manager && !loan && !inWindow && (
        <p className="input-help">
          Az átadási időablak még nem kezdődött el, vagy már véget ért.
        </p>
      )}
      {mode && (
        <form
          className="decision-panel"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <h3>
            {mode === "checkout"
              ? "Átadás megerősítése"
              : mode === "return"
                ? "Visszavétel megerősítése"
                : "Javítás és ellenőrzés megerősítése"}
          </h3>
          <fieldset disabled={mutation.isPending}>
            {mode !== "close" && (
              <label>
                {mode === "checkout"
                  ? "Átadási állapot"
                  : "Visszavételi állapot"}
                <select
                  value={condition}
                  onChange={(event) => setCondition(event.target.value)}
                >
                  {Object.entries(conditionLabels)
                    .filter(([key]) => mode === "return" || key !== "DAMAGED")
                    .map(([key, label]) => (
                      <option value={key} key={key}>
                        {label}
                      </option>
                    ))}
                </select>
              </label>
            )}
            {mode !== "checkout" && (
              <label>
                {mode === "return" && condition === "DAMAGED"
                  ? "Sérülés leírása"
                  : "Megjegyzés (nem kötelező)"}
                <textarea
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  required={mode === "return" && condition === "DAMAGED"}
                  minLength={
                    mode === "return" && condition === "DAMAGED" ? 5 : undefined
                  }
                  maxLength={mode === "close" ? 500 : 1000}
                  rows={3}
                />
              </label>
            )}
            {mode === "return" && condition === "DAMAGED" && (
              <>
                <label>
                  Tervezett karbantartásvég
                  <input
                    type="datetime-local"
                    value={endsAt}
                    onChange={(event) => setEndsAt(event.target.value)}
                    onInput={(event) => setEndsAt(event.target.value)}
                    required
                    step="60"
                  />
                </label>
                <p className="input-help">
                  Budapesti idő szerint. A visszavétel azonnali biztonsági
                  zárolást hoz létre. Az érintett későbbi foglalások
                  megmaradnak, de figyelmeztetést kapnak.
                </p>
              </>
            )}
            {mode === "close" && (
              <p className="input-help">
                Csak a javítás és a működőképesség ellenőrzése után zárd le a
                karbantartást.
              </p>
            )}
            <div className="reservation-actions">
              <button className="button primary" type="submit">
                {mutation.isPending ? "Mentés…" : "Rögzítés megerősítése"}
              </button>
              <button
                className="button secondary"
                type="button"
                onClick={() => {
                  setMode(null);
                  mutation.reset();
                }}
              >
                Mégse
              </button>
            </div>
          </fieldset>
        </form>
      )}
    </section>
  );
}
