import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, MapPin, Pencil, Plus, Tag, Trash2 } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";

const empty = () => ({
  name: "",
  description: "",
  code: "",
  building: "",
  room: "",
  version: 0,
});
const toDraft = (record) => ({
  ...empty(),
  ...record,
  description: record.description ?? "",
});
export function ReferenceDataPage({ kind }) {
  const category = kind === "categories";
  const { token } = useAuth();
  const client = useQueryClient();
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(null);
  const [draft, setDraft] = useState(empty);
  const [review, setReview] = useState(null);
  const [notice, setNotice] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(q);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [q]);
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (search) params.set("q", search);
  if (filter) params.set("active", filter);
  const result = useQuery({
    queryKey: ["reference-data", kind, params.toString()],
    queryFn: ({ signal }) => api(`/admin/${kind}?${params}`, { token, signal }),
  });
  const mutation = useMutation({
    mutationFn: ({ operation, record, payload }) => {
      if (operation === "save")
        return api(`/admin/${kind}${record ? `/${record.id}` : ""}`, {
          token,
          method: record ? "PUT" : "POST",
          body: payload,
        });
      if (operation === "delete")
        return api(`/admin/${kind}/${record.id}?version=${record.version}`, {
          token,
          method: "DELETE",
        });
      return api(`/admin/${kind}/${record.id}/active`, {
        token,
        method: "PATCH",
        body: { active: !record.active, version: record.version },
      });
    },
    onSuccess: async (saved, { operation, record }) => {
      if (operation === "save") {
        setSelectedId(saved.id);
        setDraft(toDraft(saved));
      }
      if (operation === "delete" && selectedId === record.id) {
        setSelectedId(null);
        setDraft(empty());
      }
      setReview(null);
      setNotice(
        operation === "save"
          ? "A törzsadat mentve."
          : operation === "delete"
            ? "A nem hivatkozott törzsadat törölve."
            : record.active
              ? "A törzsadat archiválva."
              : "A törzsadat újra aktív.",
      );
      await Promise.all(
        [
          "reference-data",
          "categories",
          "locations",
          "equipment",
          "availability",
          "managed-equipment",
          "equipment-picker",
        ].map((key) => client.invalidateQueries({ queryKey: [key] })),
      );
    },
    onError: (error) => {
      if (error.status === 409)
        client.invalidateQueries({ queryKey: ["reference-data", kind] });
    },
  });
  const busy = mutation.isPending || refreshing;
  function edit(record) {
    setSelectedId(record.id);
    setDraft(toDraft(record));
    setReview(null);
    setNotice("");
    setRefreshError("");
    mutation.reset();
  }
  function create() {
    setSelectedId(null);
    setDraft(empty());
    setReview(null);
    setNotice("");
    setRefreshError("");
    mutation.reset();
  }
  function change(event) {
    const { name, value } = event.target;
    setDraft((previous) => ({ ...previous, [name]: value }));
    setNotice("");
  }
  function choose(operation, record) {
    setReview({ operation, record });
    setNotice("");
    mutation.reset();
  }
  async function reload() {
    setRefreshing(true);
    setRefreshError("");
    try {
      edit(await api(`/admin/${kind}/${selectedId}`, { token }));
      setNotice("A friss adatok betöltve.");
    } catch (error) {
      setRefreshError(error.message);
    } finally {
      setRefreshing(false);
    }
  }
  function save(event) {
    event.preventDefault();
    mutation.mutate({
      operation: "save",
      record: selectedId ? { id: selectedId } : null,
      payload: {
        name: draft.name.trim(),
        ...(category
          ? { description: draft.description.trim() }
          : {
              code: draft.code.trim(),
              building: draft.building.trim(),
              room: draft.room.trim(),
            }),
        ...(selectedId ? { version: draft.version } : {}),
      },
    });
  }
  return (
    <main className="page management-page reference-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">AZ ESZKÖZTÁR ALAPJAI</span>
          <h1>Törzsadatok.</h1>
          <p>Kategóriák és tárolási helyek, megőrzött kapcsolatokkal.</p>
        </div>
        <span className="heading-symbol">
          {category ? <Tag size={28} /> : <MapPin size={28} />}
        </span>
      </div>
      <nav className="reference-tabs" aria-label="Törzsadat típusa">
        <Link
          to="/torzsadatok/kategoriak"
          aria-current={category ? "page" : undefined}
        >
          Kategóriák
        </Link>
        <Link
          to="/torzsadatok/helyszinek"
          aria-current={!category ? "page" : undefined}
        >
          Tárolási helyek
        </Link>
      </nav>
      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}
      {(mutation.isError || refreshError) && (
        <div className="form-error" role="alert">
          {refreshError || mutation.error.message}
        </div>
      )}
      <div className="reference-grid">
        <section className="reference-list">
          <div className="management-toolbar">
            <div className="reference-filters">
              <label>
                Törzsadat keresése
                <input
                  value={q}
                  onChange={(event) => setQ(event.target.value)}
                  maxLength={120}
                  placeholder={
                    category ? "Kategórianév" : "Név vagy helyszínkód"
                  }
                />
              </label>
              <label>
                Állapot
                <select
                  value={filter}
                  onChange={(event) => {
                    setFilter(event.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Minden állapot</option>
                  <option value="true">Aktív</option>
                  <option value="false">Archivált</option>
                </select>
              </label>
            </div>
            <button
              className="button secondary"
              disabled={busy}
              onClick={create}
            >
              <Plus size={16} />
              {category ? "Új kategória" : "Új tárolási hely"}
            </button>
          </div>
          {result.isPending ? (
            <p role="status">Törzsadatok betöltése…</p>
          ) : result.isError ? (
            <ErrorState error={result.error} retry={result.refetch} />
          ) : !result.data.content.length ? (
            <section className="state-panel">
              <Tag size={28} />
              <h2>Nincs ilyen törzsadat.</h2>
              <p>Módosítsd a keresést, vagy vegyél fel új rekordot.</p>
            </section>
          ) : (
            <>
              <div className="table-wrap">
                <table className="reference-table">
                  <caption>
                    {result.data.page.totalElements}{" "}
                    {category ? "kategória" : "tárolási hely"}
                  </caption>
                  <thead>
                    <tr>
                      <th>{category ? "Kategória" : "Tárolási hely"}</th>
                      <th>Állapot</th>
                      <th>Műveletek</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.data.content.map((record) => (
                      <tr key={record.id}>
                        <td>
                          <strong>{record.name}</strong>
                          {!category && (
                            <small className="table-secondary">
                              {record.code} · {record.building} · {record.room}
                            </small>
                          )}
                          <Link
                            className="reference-equipment-link"
                            to={`/kezeles?${category ? "categoryId" : "locationId"}=${record.id}`}
                          >
                            Kapcsolódó eszközök
                          </Link>
                        </td>
                        <td>
                          <span
                            className={`reservation-status ${record.active ? "status-approved" : "status-cancelled"}`}
                          >
                            {record.active ? "Aktív" : "Archivált"}
                          </span>
                        </td>
                        <td>
                          <div className="reference-row-actions">
                            <button
                              className="icon-button"
                              disabled={busy}
                              onClick={() => edit(record)}
                              aria-label={`${record.name} szerkesztése`}
                            >
                              <Pencil size={16} />
                            </button>
                            <button
                              className="icon-button"
                              disabled={busy}
                              onClick={() => choose("active", record)}
                              aria-label={`${record.name} ${record.active ? "archiválása" : "aktiválása"}`}
                            >
                              <Archive size={16} />
                            </button>
                            <button
                              className="icon-button"
                              disabled={busy}
                              onClick={() => choose("delete", record)}
                              aria-label={`${record.name} törlése`}
                            >
                              <Trash2 size={16} />
                            </button>
                          </div>
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
          {review && (
            <section
              className="editor-panel reference-review"
              aria-label="Törzsadatművelet megerősítése"
            >
              <h2>
                {review.operation === "delete"
                  ? "Törlöd ezt a törzsadatot?"
                  : review.record.active
                    ? "Archiválod ezt a törzsadatot?"
                    : "Újra aktiválod ezt a törzsadatot?"}
              </h2>
              <p>
                <strong>{review.record.name}</strong>
              </p>
              <p>
                {review.operation === "delete"
                  ? "Csak olyan rekord törölhető, amelyhez egyetlen eszköz sem tartozik. A hivatkozott rekordok az előzmények miatt megmaradnak."
                  : review.record.active
                    ? "A kapcsolódó eszközök eltűnnek a hallgatói katalógusból. Élő igény vagy kint lévő eszköz esetén az archiválást a rendszer visszautasítja."
                    : "A kapcsolódó aktív eszközök a többi feltétel teljesülése esetén újra megjelennek a katalógusban."}
              </p>
              <div className="reservation-actions">
                <button
                  className="button primary"
                  disabled={busy}
                  onClick={() => mutation.mutate(review)}
                >
                  {mutation.isPending ? "Mentés…" : "Művelet megerősítése"}
                </button>
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => setReview(null)}
                >
                  Mégse
                </button>
              </div>
            </section>
          )}
        </section>
        <section className="editor-panel reference-editor">
          <h2>
            {selectedId
              ? category
                ? "Kategória szerkesztése"
                : "Tárolási hely szerkesztése"
              : category
                ? "Új kategória adatai"
                : "Új tárolási hely adatai"}
          </h2>
          <form className="management-form" onSubmit={save}>
            <fieldset disabled={busy}>
              {!category && (
                <label>
                  Helyszínkód
                  <input
                    name="code"
                    value={draft.code}
                    onChange={change}
                    maxLength={30}
                    required
                  />
                </label>
              )}
              <label>
                Név
                <input
                  name="name"
                  value={draft.name}
                  onChange={change}
                  maxLength={category ? 80 : 100}
                  required
                />
              </label>
              {category ? (
                <label>
                  Leírás
                  <textarea
                    name="description"
                    value={draft.description}
                    onChange={change}
                    maxLength={500}
                    rows={4}
                  />
                </label>
              ) : (
                <div className="editor-columns">
                  <label>
                    Épület
                    <input
                      name="building"
                      value={draft.building}
                      onChange={change}
                      maxLength={100}
                      required
                    />
                  </label>
                  <label>
                    Helyiség
                    <input
                      name="room"
                      value={draft.room}
                      onChange={change}
                      maxLength={30}
                      required
                    />
                  </label>
                </div>
              )}
              <button className="button primary" type="submit">
                {mutation.isPending ? "Mentés…" : "Törzsadat mentése"}
              </button>
              {selectedId && (
                <>
                  <small className="input-help">
                    A friss adatok betöltése felülírja a még el nem mentett
                    mezőket.
                  </small>
                  <button
                    className="button secondary"
                    type="button"
                    onClick={reload}
                  >
                    Friss adatok betöltése
                  </button>
                </>
              )}
            </fieldset>
          </form>
        </section>
      </div>
    </main>
  );
}
