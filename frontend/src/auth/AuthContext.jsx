import { createContext, useContext, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";

const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  const [notice, setNotice] = useState("");
  const client = useQueryClient();
  function clearSession(message = "") {
    setSession(null);
    setNotice(message);
    client.clear();
  }
  useEffect(() => {
    const handler = () =>
      clearSession("A belépésed lejárt. Kérjük, jelentkezz be újra.");
    window.addEventListener("eszkozpont:unauthorized", handler);
    return () => window.removeEventListener("eszkozpont:unauthorized", handler);
  }, [client]);
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(
      () => clearSession("A belépésed lejárt. Kérjük, jelentkezz be újra."),
      Math.max(0, Date.parse(session.expiresAt) - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [session]);
  async function login(values) {
    const result = await api("/auth/login", { method: "POST", body: values });
    client.clear();
    setNotice("");
    setSession(result);
  }
  async function logout() {
    // A szerverhibát nem rejtjük el: csak sikeres visszavonás után jelentkeztetünk ki.
    await api("/auth/logout", { method: "POST", token: session.accessToken });
    clearSession("Sikeresen kijelentkeztél minden eszközön.");
  }
  return (
    <AuthContext.Provider
      value={{
        user: session?.user,
        token: session?.accessToken,
        notice,
        login,
        logout,
        endSession: clearSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export const useAuth = () => useContext(AuthContext);
