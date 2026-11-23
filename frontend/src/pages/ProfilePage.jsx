import { useState } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { roleLabels } from "../components/AppLayout";

export function ProfilePage() {
  const { user, token, updateProfile, endSession } = useAuth();
  const [displayName, setDisplayName] = useState(user.displayName);
  const [saving, setSaving] = useState(null);
  const [nameError, setNameError] = useState("");
  const [nameSaved, setNameSaved] = useState(false);
  const [passwordError, setPasswordError] = useState("");

  async function saveName(event) {
    event.preventDefault();
    setSaving("name");
    setNameError("");
    setNameSaved(false);
    try {
      const updated = await updateProfile(displayName.trim());
      setDisplayName(updated.displayName);
      setNameSaved(true);
    } catch (error) {
      setNameError(error.errors?.[0]?.message || error.message);
    } finally {
      setSaving(null);
    }
  }

  async function savePassword(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const currentPassword = fields.get("currentPassword");
    const newPassword = fields.get("newPassword");
    if (newPassword !== fields.get("confirmation")) {
      setPasswordError("A két új jelszó nem egyezik.");
      form.elements.namedItem("confirmation").focus();
      return;
    }
    setPasswordError("");
    setSaving("password");
    try {
      await api("/me/password", {
        method: "POST",
        token,
        body: { currentPassword, newPassword },
      });
      form.reset();
      endSession("A jelszavad megváltozott. Lépj be az új jelszóval.");
    } catch (error) {
      setPasswordError(error.errors?.[0]?.message || error.message);
    } finally {
      setSaving(null);
    }
  }

  return (
    <main className="page management-page">
      <div className="page-heading">
        <h1>Fiókom</h1>
      </div>
      <div className="editor-columns">
        <section className="editor-panel" aria-labelledby="profile-name">
          <h2 id="profile-name">Fiókadatok</h2>
          <dl className="detail-facts">
            <div>
              <dt>E-mail cím</dt>
              <dd>{user.email}</dd>
            </div>
            <div>
              <dt>Szerepkör</dt>
              <dd>{roleLabels[user.role]}</dd>
            </div>
          </dl>
          {nameError && (
            <div className="form-error" role="alert">{nameError}</div>
          )}
          {nameSaved && (
            <div className="notice" role="status">A neved frissült.</div>
          )}
          <form className="management-form" onSubmit={saveName}>
            <fieldset disabled={!!saving}>
              <label>
                Megjelenített név
                <input
                  value={displayName}
                  autoComplete="name"
                  onChange={(event) => {
                    setDisplayName(event.target.value);
                    setNameSaved(false);
                    setNameError("");
                  }}
                  required
                  maxLength={100}
                />
              </label>
              <button
                className="button primary"
                type="submit"
                disabled={!displayName.trim() || displayName.trim() === user.displayName}
              >
                {saving === "name" ? "Mentés…" : "Név mentése"}
              </button>
            </fieldset>
          </form>
        </section>
        <section className="editor-panel" aria-labelledby="profile-password">
          <h2 id="profile-password">Jelszócsere</h2>
          {passwordError && (
            <div className="form-error" role="alert">{passwordError}</div>
          )}
          <form className="management-form" onSubmit={savePassword}>
            <fieldset disabled={!!saving}>
              <label>
                Jelenlegi jelszó
                <input
                  type="password"
                  name="currentPassword"
                  autoComplete="current-password"
                  required
                  maxLength={64}
                />
              </label>
              <label>
                Új jelszó
                <input
                  type="password"
                  name="newPassword"
                  autoComplete="new-password"
                  required
                  minLength={12}
                  maxLength={64}
                  aria-describedby="new-password-help"
                />
              </label>
              <p id="new-password-help" className="input-help">
                12–64 karakter. Mentés után minden eszközön újra be kell jelentkezned.
              </p>
              <label>
                Új jelszó megerősítése
                <input
                  type="password"
                  name="confirmation"
                  autoComplete="new-password"
                  required
                  maxLength={64}
                />
              </label>
              <button className="button primary" type="submit">
                {saving === "password" ? "Mentés…" : "Jelszó módosítása"}
              </button>
            </fieldset>
          </form>
        </section>
      </div>
    </main>
  );
}
