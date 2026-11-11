import { useEffect, useState } from "react";
import { CalendarSearch, ChevronRight } from "lucide-react";
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
    <details className="period-search" open={Boolean(startsAt || endsAt)}>
      <summary className="period-search-heading">
        <CalendarSearch size={18} aria-hidden="true" />
        <span>Szűrés időszakra</span>
        {applied.startsAt && applied.endsAt && (
          <span className="period-applied" role="status">
            {formatDate(startsAt)} – {formatDate(endsAt)}
          </span>
        )}
        <ChevronRight className="period-chevron" size={16} aria-hidden="true" />
      </summary>
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
    </details>
  );
}
