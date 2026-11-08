import { useEffect, useState } from "react";
import { CalendarSearch } from "lucide-react";
import { fromBudapestInput, formatDate } from "../reservations/format";
import { inputPeriod } from "./calendar";

export function CatalogPeriodFilter({ startsAt, endsAt, onApply, onClear }) {
  const [draft, setDraft] = useState(() => inputPeriod({ startsAt, endsAt }));
  const [error, setError] = useState("");
  useEffect(() => {
    setDraft(inputPeriod({ startsAt, endsAt }));
    setError("");
  }, [startsAt, endsAt]);
  function change(event) {
    setDraft((previous) => ({
      ...previous,
      [event.target.name]: event.target.value,
    }));
    setError("");
  }
  function apply(event) {
    event.preventDefault();
    try {
      const start = fromBudapestInput(draft.startsAt);
      const end = fromBudapestInput(draft.endsAt);
      if (Date.parse(start) >= Date.parse(end))
        throw new Error("A visszahozás az átvétel után legyen.");
      onApply(start, end);
    } catch (failure) {
      setError(failure.message);
    }
  }
  const applied = inputPeriod({ startsAt, endsAt });
  return (
    <section className="period-search" aria-labelledby="period-search-title">
      <div className="period-search-heading">
        <CalendarSearch size={22} />
        <div>
          <h3 id="period-search-title">Mikor lenne rá szükséged?</h3>
          <p>Keress a kiválasztott időszakra igényelhető eszközök között.</p>
        </div>
      </div>
      <form onSubmit={apply} className="period-search-form">
        <label>
          Átvétel a kereséshez
          <input
            type="datetime-local"
            name="startsAt"
            value={draft.startsAt}
            onChange={change}
            onInput={change}
            step="60"
            required
          />
        </label>
        <label>
          Visszahozás a kereséshez
          <input
            type="datetime-local"
            name="endsAt"
            value={draft.endsAt}
            onChange={change}
            onInput={change}
            step="60"
            required
          />
        </label>
        <button className="button primary" type="submit">
          Szabad eszközök keresése
        </button>
        {(startsAt || endsAt) && (
          <button className="button secondary" type="button" onClick={onClear}>
            Időszak törlése
          </button>
        )}
      </form>
      <small className="input-help">
        Budapesti idő szerint · 1 óra–7 nap · legfeljebb 60 nappal előre. A
        keresés nem biztosítja az eszközt.
      </small>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {applied.startsAt && applied.endsAt && (
        <p className="period-applied" role="status">
          Alkalmazott időszak: {formatDate(startsAt)} – {formatDate(endsAt)}
        </p>
      )}
    </section>
  );
}
