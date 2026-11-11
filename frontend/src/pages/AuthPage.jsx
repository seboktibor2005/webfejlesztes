import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ArrowRight, Eye, EyeOff } from "lucide-react";
import { Brand } from "../components/Brand";
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
  const requestedPage = location.state?.from;
  const destination =
    typeof requestedPage === "string" &&
    requestedPage.startsWith("/") &&
    !requestedPage.startsWith("//")
      ? requestedPage
      : "/attekintes";
  if (user) return <Navigate to={destination} replace />;
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
      <section className="auth-form-side">
        <header className="auth-identity">
          <Brand />
          <p>Egyetemi eszköztár</p>
        </header>
        <div className="auth-form-wrap">
          <h1>{register ? "Regisztráció" : "Bejelentkezés"}</h1>
          <p className="auth-intro">
            {register
              ? "Eszközfoglaláshoz hozz létre egy fiókot."
              : "Lépj be az eszközök és a foglalásaid megtekintéséhez."}
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
                  placeholder={register ? "Legalább 12 karakter" : undefined}
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
                ? register
                  ? "Fiók létrehozása…"
                  : "Bejelentkezés…"
                : register
                  ? "Fiók létrehozása"
                  : "Bejelentkezés"}
              <ArrowRight size={18} />
            </button>
          </form>
          <div className="auth-topline">
            <span>
              {register ? "Már van fiókod?" : "Új fiókra van szükséged?"}
            </span>
            <Link to={register ? "/belepes" : "/regisztracio"}>
              {register ? "Bejelentkezés" : "Regisztráció"}
            </Link>
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
      </section>
    </main>
  );
}
