import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import { Brand } from "../components/Brand";
import { EquipmentArt } from "../components/EquipmentArt";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";

export function AuthPage({ register = false }) {
  const { user, login, notice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({
    displayName: "",
    email: location.state?.email || "",
    password: "",
  });
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  if (user) return <Navigate to="/eszkozok" replace />;
  async function submit(event) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      const email = values.email.trim();
      if (register) {
        await api("/auth/register", {
          method: "POST",
          body: { ...values, email },
        });
        navigate("/belepes", {
          replace: true,
          state: { email, registered: true },
        });
      } else {
        await login({ email, password: values.password });
        const destination = location.state?.from;
        navigate(
          destination?.startsWith("/") && !destination.startsWith("//")
            ? destination
            : "/eszkozok",
          { replace: true },
        );
      }
    } catch (failure) {
      setError(failure);
    } finally {
      setPending(false);
    }
  }
  const update = (event) =>
    setValues({ ...values, [event.target.name]: event.target.value });
  const fieldError = (name) =>
    error?.errors?.find((e) => e.field === name)?.message;
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Brand light />
        <div className="auth-story-body">
          <span className="eyebrow light">
            <span className="live-dot" />
            EGYETEMI ESZKÖZTÁR
          </span>
          <h1>
            Az ötlet a tiéd.
            <br />
            Az eszköz <em>közös.</em>
          </h1>
          <p>
            Kamerától a fejlesztőkészletig. Találd meg a következő projektedhez
            illő eszközt egy helyen.
          </p>
          <div className="auth-art">
            <EquipmentArt code="FOTO-001" large />
            <span className="art-note">
              <Sparkles size={16} />
              Egy jó ötlet itt kezdődik.
            </span>
          </div>
          <div className="story-points">
            <span>
              <Check size={17} />
              Átlátható eszköztár
            </span>
            <span>
              <Check size={17} />
              Egyetemi közösség
            </span>
          </div>
        </div>
        <span className="auth-footnote">
          ESZKÖZPONT / ALKOSS TÖBBET, KÖZÖSEN.
        </span>
      </section>
      <section className="auth-form-side">
        <div className="auth-topline">
          <span>
            {register ? "Már van fiókod?" : "Most jársz itt először?"}
          </span>
          <Link to={register ? "/belepes" : "/regisztracio"}>
            {register ? "Bejelentkezés" : "Fiók létrehozása"}
            <ArrowRight size={15} />
          </Link>
        </div>
        <div className="auth-form-wrap">
          <div className="small-label">KEZDJÜK AZ ELEJÉN</div>
          <h2>
            {register ? "Csatlakozz az alkotókhoz." : "Jó, hogy itt vagy."}
          </h2>
          <p className="auth-intro">
            {register
              ? "Hozd létre a fiókod, és fedezd fel az egyetemi eszköztárat."
              : "Jelentkezz be, és nézz körül az eszköztárban."}
          </p>
          {(notice || location.state?.registered) && (
            <div className="notice" role="status">
              {location.state?.registered
                ? "A fiókod elkészült. Most már bejelentkezhetsz."
                : notice}
            </div>
          )}
          <form onSubmit={submit} className="auth-form">
            {register && (
              <label>
                Teljes név
                <input
                  name="displayName"
                  autoComplete="name"
                  value={values.displayName}
                  onChange={update}
                  required
                  maxLength={100}
                  placeholder="Ahogy szólíthatunk"
                  aria-invalid={!!fieldError("displayName")}
                />
                {fieldError("displayName") && (
                  <small className="field-error">
                    {fieldError("displayName")}
                  </small>
                )}
              </label>
            )}
            <label>
              E-mail-cím
              <input
                name="email"
                type="email"
                autoComplete="email"
                value={values.email}
                onChange={update}
                required
                maxLength={254}
                placeholder="nev@egyetem.hu"
                aria-invalid={!!fieldError("email")}
              />
              {fieldError("email") && (
                <small className="field-error">{fieldError("email")}</small>
              )}
            </label>
            <label>
              Jelszó
              <span className="password-field">
                <input
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete={register ? "new-password" : "current-password"}
                  value={values.password}
                  onChange={update}
                  required
                  minLength={register ? 12 : undefined}
                  maxLength={64}
                  placeholder={
                    register ? "Legalább 12 karakter" : "A jelszavad"
                  }
                  aria-invalid={!!fieldError("password")}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={
                    showPassword ? "Jelszó elrejtése" : "Jelszó megjelenítése"
                  }
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
              {fieldError("password") && (
                <small className="field-error">{fieldError("password")}</small>
              )}
            </label>
            {error && (
              <div className="form-error" role="alert">
                {error.message}
              </div>
            )}
            <button
              className="button primary full"
              type="submit"
              disabled={pending}
            >
              {pending
                ? "Egy pillanat…"
                : register
                  ? "Fiók létrehozása"
                  : "Bejelentkezés"}
              <ArrowRight size={18} />
            </button>
          </form>
          <div className="auth-security">
            <ShieldCheck size={17} />
            <span>A fiókod és az adataid védve vannak.</span>
          </div>
          <details className="demo-access">
            <summary>Bemutatófiókok helyi kipróbáláshoz</summary>
            <p>
              Hallgató: anna@eszkozpont.test
              <br />
              Eszközfelelős: kezelo@eszkozpont.test
              <br />
              Admin: admin@eszkozpont.test
              <br />
              Jelszó: <code>EszkozPont2026!</code>
            </p>
            <small>Csak a helyi demóadatok bekapcsolásakor léteznek.</small>
          </details>
        </div>
        <footer className="auth-form-footer">
          Közös eszközök. Saját történetek.
        </footer>
      </section>
    </main>
  );
}
