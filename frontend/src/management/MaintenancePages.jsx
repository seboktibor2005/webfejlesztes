import { useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Wrench } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import {
  formatDate,
  fromBudapestInput,
  toBudapestInput,
} from "../reservations/format";
import { EquipmentHistory } from "./EquipmentEditorPage";

const labels = { OPEN: "Nyitott", CLOSED: "Lezárt", CANCELLED: "Lemondva" };
function Status({ period }) {
  return (
    <span
      className={`reservation-status ${period.overdue ? "status-rejected" : period.status === "OPEN" ? "status-requested" : "status-completed"}`}
    >
      {period.overdue ? "Túlfutott, zárolás aktív" : labels[period.status]}
    </span>
  );
}
export function MaintenancePage() {
  const { token } = useAuth();
  const [url] = useSearchParams();
  const equipmentId = url.get("equipmentId") || "";
  const [status, setStatus] = useState("OPEN");
  const [page, setPage] = useState(0);
  const query = new URLSearchParams({ page: String(page), size: "12" });
  if (status) query.set("status", status);
  if (equipmentId) query.set("equipmentId", equipmentId);
  const result = useQuery({
    queryKey: ["maintenance-list", query.toString()],
    refetchInterval: 30_000,
    queryFn: ({ signal }) =>
      api(`/management/maintenance?${query}`, { token, signal }),
  });
  return (
    <main className="page management-page reservation-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">ÜZEMBIZTOS ESZKÖZTÁR</span>
          <h1>Karbantartások.</h1>
          <p>
            Tervezett javítások, műszaki ellenőrzések és sérülési zárolások.
          </p>
        </div>
        <span className="heading-symbol">
          <Wrench size={28} />
        </span>
      </div>
      <div className="management-toolbar">
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
              {Object.entries(labels).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {equipmentId && (
          <Link className="back-link" to="/karbantartasok">
            Eszközszűrő törlése
          </Link>
        )}
        <Link className="button primary" to="/karbantartasok/uj">
          Karbantartás rögzítése
        </Link>
      </div>
      {result.isPending ? (
        <p role="status">Karbantartások betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : !result.data.content.length ? (
        <section className="state-panel">
          <Wrench size={34} />
          <h2>Nincs ilyen karbantartás.</h2>
          <p>Válassz másik állapotot, vagy rögzíts új időszakot.</p>
        </section>
      ) : (
        <>
          <div className="table-wrap">
            <table className="reservation-table">
              <caption>{result.data.page.totalElements} karbantartás</caption>
              <thead>
                <tr>
                  <th>Eszköz</th>
                  <th>Időszak</th>
                  <th>Indok</th>
                  <th>Állapot</th>
                  <th>
                    <span className="sr-only">Részletek</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((period) => (
                  <tr key={period.id}>
                    <td>
                      <strong>{period.equipment.name}</strong>
                      <small className="table-secondary">
                        {period.equipment.inventoryCode}
                      </small>
                    </td>
                    <td>
                      {formatDate(period.startsAt)}
                      <small className="table-secondary">
                        {formatDate(period.endsAt)}
                      </small>
                    </td>
                    <td className="maintenance-reason">{period.reason}</td>
                    <td>
                      <Status period={period} />
                    </td>
                    <td>
                      <Link
                        className="table-detail-link"
                        to={`/karbantartasok/${period.id}`}
                        aria-label={`Karbantartás #${period.id} részletei`}
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
    </main>
  );
}

export function MaintenanceCreatePage() {
  const { token } = useAuth();
  const client = useQueryClient();
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [selected, setSelected] = useState(null);
  const [start, setStart] = useState(() => toBudapestInput(new Date()));
  const [end, setEnd] = useState(() =>
    toBudapestInput(new Date(Date.now() + 7_200_000)),
  );
  const [reason, setReason] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(timer);
  }, [search]);
  const equipment = useQuery({
    queryKey: ["equipment-picker", debounced],
    queryFn: ({ signal }) =>
      api(
        `/management/equipment?size=20&q=${encodeURIComponent(debounced)}&sort=name,asc`,
        { token, signal },
      ),
  });
  const items = equipment.data?.content || [];
  const choices =
    selected && !items.some((item) => item.id === selected.id)
      ? [selected, ...items]
      : items;
  const mutation = useMutation({
    mutationFn: () =>
      api("/management/maintenance", {
        token,
        method: "POST",
        body: {
          equipmentId: selected.id,
          startsAt: fromBudapestInput(start),
          endsAt: fromBudapestInput(end),
          reason: reason.trim(),
        },
      }),
    onSuccess: (saved) => {
      client.invalidateQueries({ queryKey: ["maintenance-list"] });
      client.invalidateQueries({ queryKey: ["reservations"] });
      client.invalidateQueries({ queryKey: ["equipment-events"] });
      navigate(`/karbantartasok/${saved.id}`);
    },
  });
  return (
    <main className="page management-page">
      <Link className="back-link" to="/karbantartasok">
        <ArrowLeft size={17} />
        Vissza a karbantartásokhoz
      </Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">TERVEZÉS ÉS JAVÍTÁS</span>
          <h1>Új karbantartás.</h1>
          <p>
            Jóváhagyott foglalás vagy kint lévő eszköz esetén előbb rendezd az
            ütközést.
          </p>
        </div>
      </div>
      <section className="editor-panel">
        <form
          className="management-form"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <fieldset disabled={mutation.isPending}>
            <label>
              Eszköz keresése
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                maxLength={120}
                placeholder="Név vagy leltári szám"
              />
            </label>
            {equipment.isError && (
              <ErrorState error={equipment.error} retry={equipment.refetch} />
            )}
            <label>
              Eszköz
              <select
                required
                value={selected ? String(selected.id) : ""}
                onChange={(event) =>
                  setSelected(
                    choices.find(
                      (item) => String(item.id) === event.target.value,
                    ) || null,
                  )
                }
              >
                <option value="">
                  {equipment.isPending
                    ? "Eszközök betöltése…"
                    : "Válassz eszközt"}
                </option>
                {choices.map((item) => (
                  <option
                    key={item.id}
                    value={item.id}
                    disabled={item.lifecycle === "RETIRED"}
                  >
                    {item.name} · {item.inventoryCode}
                    {item.lifecycle === "RETIRED" ? " (kivonva)" : ""}
                  </option>
                ))}
              </select>
            </label>
            <small className="input-help">
              Legfeljebb 20 találat jelenik meg; további eszközhöz pontosítsd a
              keresést.
            </small>
            <div className="editor-columns">
              <label>
                Karbantartás kezdete
                <input
                  type="datetime-local"
                  value={start}
                  onChange={(event) => setStart(event.target.value)}
                  onInput={(event) => setStart(event.target.value)}
                  step="60"
                  required
                />
              </label>
              <label>
                Tervezett befejezés
                <input
                  type="datetime-local"
                  value={end}
                  onChange={(event) => setEnd(event.target.value)}
                  onInput={(event) => setEnd(event.target.value)}
                  step="60"
                  required
                />
              </label>
            </div>
            <small className="input-help">
              Budapesti idő szerint. Már elkezdett javítás is rögzíthető,
              jövőbeli tervezett befejezéssel. A zárolás a lezárásig megmarad.
            </small>
            <label>
              Karbantartás indoka
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                required
                minLength={5}
                maxLength={500}
                rows={4}
              />
            </label>
            {mutation.isError && (
              <div className="form-error" role="alert">
                {mutation.error.message}
              </div>
            )}
            <button className="button primary" type="submit">
              {mutation.isPending ? "Mentés…" : "Karbantartás mentése"}
            </button>
          </fieldset>
        </form>
      </section>
    </main>
  );
}

export function MaintenanceDetailPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const client = useQueryClient();
  const [action, setAction] = useState(null);
  const [note, setNote] = useState("");
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  const result = useQuery({
    queryKey: ["maintenance", id],
    refetchInterval: 30_000,
    queryFn: ({ signal }) =>
      api(`/management/maintenance/${id}`, { token, signal }),
  });
  const refresh = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ["maintenance", id] }),
      client.invalidateQueries({ queryKey: ["maintenance-list"] }),
      client.invalidateQueries({ queryKey: ["equipment-events"] }),
      client.invalidateQueries({ queryKey: ["reservations"] }),
      client.invalidateQueries({ queryKey: ["reservation"] }),
      client.invalidateQueries({ queryKey: ["reservation-events"] }),
      client.invalidateQueries({ queryKey: ["loans"] }),
    ]);
  const mutation = useMutation({
    mutationFn: () =>
      api(`/management/maintenance/${id}/${action}`, {
        token,
        method: "POST",
        body:
          action === "cancel"
            ? { reason: note.trim() }
            : note.trim()
              ? { note: note.trim() }
              : {},
      }),
    onSuccess: async () => {
      await refresh();
      setAction(null);
      setNote("");
      setNotice("A karbantartás állapota és az eseménytörténet frissült.");
    },
    onError: async (error) => {
      if (error.status === 409) await refresh();
    },
  });
  function choose(value) {
    setAction(value);
    setNote("");
    setNotice("");
    mutation.reset();
  }
  const period = result.data;
  return (
    <main className="page management-page">
      <Link className="back-link" to="/karbantartasok">
        <ArrowLeft size={17} />
        Vissza a karbantartásokhoz
      </Link>
      {result.isPending ? (
        <p role="status">Karbantartás betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <>
          <div className="page-heading">
            <div>
              <span className="eyebrow">KARBANTARTÁS · #{period.id}</span>
              <h1>{period.equipment.name}</h1>
              <p>{period.equipment.inventoryCode}</p>
            </div>
            <Status period={period} />
          </div>
          <section className="editor-panel">
            <h2>A javítás részletei</h2>
            <dl className="detail-facts">
              <div>
                <dt>Kezdet</dt>
                <dd>{formatDate(period.startsAt)}</dd>
              </div>
              <div>
                <dt>Tervezett befejezés</dt>
                <dd>{formatDate(period.endsAt)}</dd>
              </div>
              <div>
                <dt>Rögzítette</dt>
                <dd>{period.createdBy.displayName}</dd>
              </div>
              {period.closedAt && (
                <div>
                  <dt>Lezárva</dt>
                  <dd>{formatDate(period.closedAt)}</dd>
                </div>
              )}
            </dl>
            <h3>Indok</h3>
            <p className="reservation-purpose">{period.reason}</p>
            {period.status === "OPEN" && (
              <p className="loan-warning">
                A zárolás a tervezett befejezés után is aktív marad a javítás
                ellenőrzéséig és kezelői lezárásáig.
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
            {period.status === "OPEN" && (
              <div className="reservation-actions">
                {Date.parse(period.startsAt) <= now ? (
                  <button
                    className="button primary"
                    disabled={mutation.isPending}
                    onClick={() => choose("close")}
                  >
                    Karbantartás lezárása
                  </button>
                ) : (
                  <button
                    className="button secondary"
                    disabled={mutation.isPending}
                    onClick={() => choose("cancel")}
                  >
                    Tervezett karbantartás lemondása
                  </button>
                )}
              </div>
            )}
            {action && period.status === "OPEN" && (
              <form
                className="management-form decision-panel"
                onSubmit={(event) => {
                  event.preventDefault();
                  mutation.mutate();
                }}
              >
                <h3>
                  {action === "cancel"
                    ? "Lemondás megerősítése"
                    : "Javítás és ellenőrzés megerősítése"}
                </h3>
                <fieldset disabled={mutation.isPending}>
                  <label>
                    {action === "cancel"
                      ? "Lemondás indoka"
                      : "Lezárási megjegyzés (nem kötelező)"}
                    <textarea
                      value={note}
                      onChange={(event) => setNote(event.target.value)}
                      required={action === "cancel"}
                      minLength={action === "cancel" ? 5 : undefined}
                      maxLength={500}
                      rows={3}
                    />
                  </label>
                  <div className="reservation-actions">
                    <button className="button primary" type="submit">
                      {mutation.isPending ? "Mentés…" : "Művelet megerősítése"}
                    </button>
                    <button
                      className="button secondary"
                      type="button"
                      onClick={() => setAction(null)}
                    >
                      Mégse
                    </button>
                  </div>
                </fieldset>
              </form>
            )}
            <div className="reservation-actions">
              <Link
                className="button secondary"
                to={`/kezeles/${period.equipment.id}`}
              >
                Eszköz adatlapja
              </Link>
              {period.reservationId && (
                <Link
                  className="button secondary"
                  to={`/foglalasok/${period.reservationId}`}
                >
                  Sérült visszavétel megnyitása
                </Link>
              )}
            </div>
          </section>
          <EquipmentHistory id={period.equipment.id} />
        </>
      )}
    </main>
  );
}
