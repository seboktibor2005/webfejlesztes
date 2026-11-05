import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Users, ChevronLeft, ChevronRight } from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { roleLabels } from "../components/AppLayout";

export function UsersPage() {
  const { token } = useAuth();
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["users", page],
    queryFn: ({ signal }) =>
      api(`/admin/users?page=${page}&size=20`, { token, signal }),
  });
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">ADMINISZTRÁCIÓ</span>
          <h1>Az alkotóközösség.</h1>
          <p>Regisztrált felhasználók és jogosultsági szintjeik.</p>
        </div>
        <span className="heading-symbol">
          <Users size={30} />
        </span>
      </div>
      {result.isPending ? (
        <p role="status">Felhasználók betöltése…</p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <caption>
                {result.data.page.totalElements} regisztrált felhasználó
              </caption>
              <thead>
                <tr>
                  <th>Név</th>
                  <th>E-mail-cím</th>
                  <th>Szerepkör</th>
                  <th>Állapot</th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.displayName}</strong>
                    </td>
                    <td>{user.email}</td>
                    <td>
                      <span className="role-badge">
                        {roleLabels[user.role]}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`status-text ${user.enabled ? "" : "disabled"}`}
                      >
                        {user.enabled ? "Aktív" : "Letiltva"}
                      </span>
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
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  <ChevronLeft size={16} />
                  Előző
                </button>
                <button
                  className="button secondary"
                  disabled={page + 1 >= result.data.page.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Következő
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </main>
  );
}
