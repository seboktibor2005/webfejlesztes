import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { roleLabels } from "../components/AppLayout";

export function UserEditorPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const result = useQuery({
    queryKey: ["admin-user", id],
    queryFn: ({ signal }) => api(`/admin/users/${id}`, { token, signal }),
  });
  return (
    <main className="page management-page">
      <Link className="back-link" to="/felhasznalok">
        <ArrowLeft size={17} />
        Vissza a felhasználókhoz
      </Link>
      <div className="page-heading">
        <div>
          <h1>Felhasználó szerkesztése</h1>
          <p>A fiók hozzáférését a szerver is ellenőrzi.</p>
        </div>
      </div>
      {result.isPending ? (
        <p role="status">Felhasználó betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <UserForm key={id} original={result.data} reload={result.refetch} />
      )}
    </main>
  );
}
const values = (user) => ({
  displayName: user.displayName,
  role: user.role,
  enabled: user.enabled,
  version: user.version,
});
function UserForm({ original, reload }) {
  const { token, user: actor, endSession } = useAuth();
  const client = useQueryClient();
  const [draft, setDraft] = useState(() => values(original));
  const [review, setReview] = useState(false);
  const [notice, setNotice] = useState("");
  const mutation = useMutation({
    mutationFn: () =>
      api(`/admin/users/${original.id}`, {
        token,
        method: "PATCH",
        body: { ...draft, displayName: draft.displayName.trim() },
      }),
    onSuccess: (saved) => {
      if (saved.id === actor.id && saved.version !== draft.version) {
        endSession("A saját fiókod módosult. Kérjük, jelentkezz be újra.");
        return;
      }
      setDraft(values(saved));
      setReview(false);
      setNotice(
        "A felhasználó adatai mentve. Változás esetén a korábbi belépései érvényüket vesztették.",
      );
      client.setQueryData(["admin-user", String(saved.id)], saved);
      client.invalidateQueries({ queryKey: ["users"] });
    },
  });
  async function fresh() {
    const result = await reload();
    if (result.data) {
      setDraft(values(result.data));
      setReview(false);
      mutation.reset();
    }
  }
  return (
    <section className="editor-panel">
      <h2>
        <ShieldCheck size={20} />
        {original.email}
      </h2>
      <p className="input-help">
        Az e-mail-cím és a jelszó ezen a felületen nem módosítható. A rendszer
        megvédi az utolsó aktív adminisztrátort.
      </p>
      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}
      {mutation.isError && (
        <div className="form-error" role="alert">
          {mutation.error.message}
          {mutation.error.code === "STALE_VERSION" && (
            <>
              <p>A betöltés visszaállítja az űrlapot a jelenlegi adatokra.</p>
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
          mutation.reset();
          setReview(true);
        }}
      >
        <fieldset disabled={mutation.isPending || review}>
          <label>
            Megjelenített név
            <input
              value={draft.displayName}
              onChange={(event) => {
                const value = event.target.value;
                setDraft((previous) => ({ ...previous, displayName: value }));
              }}
              required
              maxLength={100}
            />
          </label>
          <label>
            Szerepkör
            <select
              value={draft.role}
              onChange={(event) => {
                const value = event.target.value;
                setDraft((previous) => ({ ...previous, role: value }));
              }}
            >
              {Object.entries(roleLabels).map(([role, label]) => (
                <option key={role} value={role}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={draft.enabled}
              onChange={(event) => {
                const value = event.target.checked;
                setDraft((previous) => ({ ...previous, enabled: value }));
              }}
            />
            A fiók aktív
          </label>
          <button className="button primary" type="submit">
            Változtatások áttekintése
          </button>
        </fieldset>
      </form>
      {review && (
        <section
          className="decision-panel"
          aria-label="Változtatások megerősítése"
        >
          <h3>Mentés előtt ellenőrizd az adatokat</h3>
          <dl className="detail-facts">
            <div>
              <dt>Név</dt>
              <dd>{draft.displayName.trim()}</dd>
            </div>
            <div>
              <dt>Szerepkör</dt>
              <dd>{roleLabels[draft.role]}</dd>
            </div>
            <div>
              <dt>Hozzáférés</dt>
              <dd>{draft.enabled ? "Aktív" : "Letiltva"}</dd>
            </div>
          </dl>
          <p className="input-help">
            Módosítás esetén a felhasználónak újra be kell jelentkeznie.
            {original.id === actor.id &&
              " A saját fiókodat szerkeszted; mentés után a belépési oldalra kerülsz."}
          </p>
          <div className="reservation-actions">
            <button
              className="button primary"
              disabled={mutation.isPending}
              onClick={() => mutation.mutate()}
            >
              {mutation.isPending ? "Mentés…" : "Módosítás megerősítése"}
            </button>
            <button
              className="button secondary"
              disabled={mutation.isPending}
              onClick={() => setReview(false)}
            >
              Vissza a szerkesztéshez
            </button>
          </div>
        </section>
      )}
    </section>
  );
}
