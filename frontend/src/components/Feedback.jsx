import { AlertCircle, SearchX, RotateCw } from "lucide-react";
export function ErrorState({ error, retry }) {
  return (
    <div className="state-panel" role="alert">
      <AlertCircle size={30} />
      <h3>Most nem sikerült betölteni</h3>
      <p>{error?.message || "Váratlan hiba történt."}</p>
      {retry && (
        <button className="button secondary" onClick={retry}>
          <RotateCw size={16} />
          Újrapróbálom
        </button>
      )}
    </div>
  );
}
export function EmptyState({ reset }) {
  return (
    <div className="state-panel">
      <SearchX size={34} />
      <h3>Erre most nincs találat</h3>
      <p>Próbálj másik kifejezést, vagy állítsd vissza a szűrőket.</p>
      <button className="button secondary" onClick={reset}>
        Szűrők törlése
      </button>
    </div>
  );
}
export function LoadingCards() {
  return (
    <div
      className="equipment-grid"
      role="status"
      aria-label="Eszközök betöltése"
    >
      {Array.from({ length: 6 }, (_, i) => (
        <div className="skeleton-card" key={i}>
          <div />
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}
