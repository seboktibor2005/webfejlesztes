import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, History, Save } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { formatDate } from "../reservations/format";

const eventLabels = {
  EQUIPMENT_CREATED: "Eszköz felvéve",
  EQUIPMENT_UPDATED: "Nyilvántartás módosítva",
  EQUIPMENT_RETIRED: "Eszköz kivonva",
  MAINTENANCE_OPENED: "Karbantartás rögzítve",
  MAINTENANCE_CLOSED: "Karbantartás lezárva",
  MAINTENANCE_CANCELLED: "Karbantartás lemondva",
  RESERVATION_CREATED: "Igény benyújtva",
  RESERVATION_APPROVED: "Igény jóváhagyva",
  RESERVATION_REJECTED: "Igény elutasítva",
  RESERVATION_CANCELLED: "Igény visszavonva",
  RESERVATION_EXPIRED: "Átvétel nélkül lejárt",
  LOAN_ISSUED: "Eszköz átadva",
  LOAN_RETURNED: "Eszköz visszavéve",
};
export function EquipmentHistory({ id }) {
  const { token } = useAuth();
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["equipment-events", String(id), page],
    queryFn: ({ signal }) =>
      api(`/management/equipment/${id}/events?page=${page}&size=10`, {
        token,
        signal,
      }),
  });
  return (
    <section className="editor-panel">
      <h2>
        <History size={19} />
        Az eszköz eseményei
      </h2>
      {result.isPending ? (
        <p role="status">Események betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : !result.data.content.length ? (
        <p className="input-help">Még nincs rögzített esemény.</p>
      ) : (
        <>
          <ol className="event-timeline">
            {result.data.content.map((event) => (
              <li key={event.id}>
                <strong>
                  {eventLabels[event.eventType] || "Nyilvántartási esemény"}
                </strong>
                <span>
                  {event.actor?.displayName || "Rendszer"} ·{" "}
                  {formatDate(event.occurredAt)}
                </span>
                {event.note && <p>{event.note}</p>}
              </li>
            ))}
          </ol>
          {result.data.page.totalPages > 1 && (
            <div className="pagination">
              <span>{page + 1}. oldal</span>
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
    </section>
  );
}
const fields = (item) => ({
  inventoryCode: item?.inventoryCode || "",
  name: item?.name || "",
  description: item?.description || "",
  categoryId: item ? String(item.category.id) : "",
  locationId: item ? String(item.location.id) : "",
  version: item?.version ?? 0,
});

export function EquipmentEditorPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const item = useQuery({
    queryKey: ["managed-equipment", id],
    enabled: !!id,
    queryFn: ({ signal }) =>
      api(`/management/equipment/${id}`, { token, signal }),
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: ({ signal }) => api("/categories", { token, signal }),
  });
  const locations = useQuery({
    queryKey: ["locations"],
    queryFn: ({ signal }) => api("/locations", { token, signal }),
  });
  const error = item.error || categories.error || locations.error;
  return (
    <main className="page management-page">
      <Link className="back-link" to="/kezeles">
        <ArrowLeft size={17} />
        Vissza az eszközállományhoz
      </Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">INTÉZMÉNYI NYILVÁNTARTÁS</span>
          <h1>{id ? "Eszköz szerkesztése." : "Új eszköz."}</h1>
          <p>Pontos adatok, követhető előzmények.</p>
        </div>
      </div>
      {error ? (
        <ErrorState
          error={error}
          retry={() => {
            if (id) item.refetch();
            categories.refetch();
            locations.refetch();
          }}
        />
      ) : (id && item.isPending) ||
        categories.isPending ||
        locations.isPending ? (
        <p role="status">Adatok betöltése…</p>
      ) : (
        <EquipmentForm
          key={id || "new"}
          item={item.data}
          categories={categories.data}
          locations={locations.data}
          reload={item.refetch}
        />
      )}
      {id && item.isSuccess && <EquipmentHistory id={id} />}
    </main>
  );
}
function EquipmentForm({ item, categories, locations, reload }) {
  const { token } = useAuth();
  const client = useQueryClient();
  const navigate = useNavigate();
  const [draft, setDraft] = useState(() => fields(item));
  const [reason, setReason] = useState("");
  const [retiring, setRetiring] = useState(false);
  const [notice, setNotice] = useState("");
  const mutation = useMutation({
    mutationFn: (command) =>
      api(
        `/management/equipment${item ? `/${item.id}${command === "retire" ? "/retire" : ""}` : ""}`,
        {
          token,
          method: command === "retire" || !item ? "POST" : "PUT",
          body:
            command === "retire"
              ? { version: draft.version, reason: reason.trim() }
              : {
                  ...(item
                    ? { version: draft.version }
                    : { inventoryCode: draft.inventoryCode }),
                  name: draft.name.trim(),
                  description: draft.description.trim(),
                  categoryId: Number(draft.categoryId),
                  locationId: Number(draft.locationId),
                },
        },
      ),
    onSuccess: (saved) => {
      setDraft(fields(saved));
      setRetiring(false);
      setReason("");
      setNotice("Az eszköz adatai mentve.");
      client.setQueryData(["managed-equipment", String(saved.id)], saved);
      client.invalidateQueries({ queryKey: ["equipment"] });
      client.invalidateQueries({ queryKey: ["availability"] });
      client.invalidateQueries({ queryKey: ["equipment-picker"] });
      client.invalidateQueries({ queryKey: ["equipment-events"] });
      if (!item) navigate(`/kezeles/${saved.id}`, { replace: true });
    },
  });
  function change(event) {
    const { name, value } = event.target;
    setDraft((previous) => ({ ...previous, [name]: value }));
    setNotice("");
  }
  async function fresh() {
    const result = await reload();
    if (result.data) {
      setDraft(fields(result.data));
      mutation.reset();
      setNotice("A friss adatok betöltve.");
    }
  }
  return (
    <section className="editor-panel">
      <h2>
        {item ? item.inventoryCode : "Nyilvántartási adatok"}
        {item && (
          <span
            className={`reservation-status ${item.lifecycle === "ACTIVE" ? "status-approved" : "status-cancelled"}`}
          >
            {item.lifecycle === "ACTIVE" ? "Aktív eszköz" : "Kivonva"}
          </span>
        )}
      </h2>
      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}
      {mutation.isError && (
        <div className="form-error" role="alert">
          {mutation.error.message}
          {mutation.error.status === 409 && item && (
            <>
              <p>
                A friss adatok betöltése felülírja a még el nem mentett mezőket.
              </p>
              <button className="button secondary" onClick={fresh}>
                Friss adatok betöltése
              </button>
            </>
          )}
        </div>
      )}
      <form
        className="management-form"
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate("save");
        }}
      >
        <fieldset disabled={mutation.isPending}>
          <div className="editor-columns">
            <label>
              Leltári szám
              <input
                name="inventoryCode"
                value={draft.inventoryCode}
                onChange={change}
                required
                maxLength={40}
                readOnly={!!item}
              />
            </label>
            <label>
              Eszköz neve
              <input
                name="name"
                value={draft.name}
                onChange={change}
                required
                maxLength={120}
              />
            </label>
          </div>
          <label>
            Leírás
            <textarea
              name="description"
              value={draft.description}
              onChange={change}
              maxLength={2000}
              rows={4}
            />
          </label>
          <div className="editor-columns">
            <label>
              Kategória
              <select
                name="categoryId"
                value={draft.categoryId}
                onChange={change}
                required
              >
                <option value="">Válassz kategóriát</option>
                {item &&
                  !categories.some(
                    (category) => category.id === item.category.id,
                  ) && (
                    <option value={item.category.id}>
                      {item.category.name} (archivált)
                    </option>
                  )}
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Tárolási hely
              <select
                name="locationId"
                value={draft.locationId}
                onChange={change}
                required
              >
                <option value="">Válassz tárolási helyet</option>
                {item &&
                  !locations.some(
                    (location) => location.id === item.location.id,
                  ) && (
                    <option value={item.location.id}>
                      {item.location.name} (archivált)
                    </option>
                  )}
                {locations.map((location) => (
                  <option key={location.id} value={location.id}>
                    {location.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {item &&
            (!categories.some((category) => category.id === item.category.id) ||
              !locations.some(
                (location) => location.id === item.location.id,
              )) && (
              <p className="input-help">
                Az eszköz jelenlegi kategóriája vagy tárolási helye archivált. A
                mentéshez válassz aktív törzsadatokat, vagy adminisztrátorként
                aktiváld újra a meglévőket.
              </p>
            )}
          <button className="button primary" type="submit">
            <Save size={17} />
            {mutation.isPending ? "Mentés…" : "Eszköz mentése"}
          </button>
        </fieldset>
      </form>
      {item?.lifecycle === "ACTIVE" && (
        <div className="editor-secondary-action">
          <h3>Kivonás a használatból</h3>
          <p>
            Az előzmények megmaradnak. Nyitott kölcsönzés vagy élő foglalási
            igény esetén előbb ezeket kell rendezni.
          </p>
          {!retiring ? (
            <button
              className="button secondary"
              disabled={mutation.isPending}
              onClick={() => {
                setRetiring(true);
                mutation.reset();
              }}
            >
              Eszköz kivonása
            </button>
          ) : (
            <form
              className="management-form"
              onSubmit={(event) => {
                event.preventDefault();
                mutation.mutate("retire");
              }}
            >
              <fieldset disabled={mutation.isPending}>
                <label>
                  Kivonás indoka
                  <textarea
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    minLength={5}
                    maxLength={500}
                    required
                    rows={3}
                  />
                </label>
                <div className="reservation-actions">
                  <button className="button primary" type="submit">
                    Kivonás megerősítése
                  </button>
                  <button
                    className="button secondary"
                    type="button"
                    onClick={() => setRetiring(false)}
                  >
                    Mégse
                  </button>
                </div>
              </fieldset>
            </form>
          )}
        </div>
      )}
      {item && (
        <Link
          className="back-link editor-related-link"
          to={`/karbantartasok?equipmentId=${item.id}`}
        >
          Az eszköz karbantartásai
        </Link>
      )}
    </section>
  );
}
