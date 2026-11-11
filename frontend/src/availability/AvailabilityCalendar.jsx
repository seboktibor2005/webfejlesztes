import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  RotateCw,
} from "lucide-react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorState } from "../components/Feedback";
import { formatDate, toBudapestInput } from "../reservations/format";
import {
  addCalendarDays,
  intervalsOnDay,
  weekDays,
  weekStart,
} from "./calendar";

const weekdayFormat = new Intl.DateTimeFormat("hu-HU", {
  timeZone: "Europe/Budapest",
  weekday: "short",
});
const dateFormat = new Intl.DateTimeFormat("hu-HU", {
  timeZone: "Europe/Budapest",
  month: "short",
  day: "numeric",
});

export function AvailabilityCalendar({ equipmentId, initialStart }) {
  const { token } = useAuth();
  const [monday, setMonday] = useState(() => weekStart(initialStart));
  const days = weekDays(monday);
  const from = days[0].start;
  const to = days[6].end;
  const params = new URLSearchParams({ from, to });
  const result = useQuery({
    queryKey: ["availability", equipmentId, from, to],
    queryFn: ({ signal }) =>
      api(`/equipment/${equipmentId}/availability?${params}`, {
        token,
        signal,
      }),
    refetchInterval: 30_000,
  });
  const blocked = result.data?.overdueLoan || result.data?.overdueMaintenance;
  const today = toBudapestInput(new Date()).slice(0, 10);
  return (
    <section
      className="availability-calendar"
      aria-labelledby="availability-heading"
    >
      <div className="calendar-heading">
        <div>
          <h2 id="availability-heading">
            <CalendarDays size={23} /> Foglaltsági naptár
          </h2>
          <p>
            Jóváhagyott időszakok és nyitott karbantartások, budapesti idő
            szerint.
          </p>
        </div>
        <button
          className="button secondary"
          onClick={() => result.refetch()}
          disabled={result.isFetching}
        >
          <RotateCw size={15} />
          Frissítés
        </button>
      </div>
      <div className="calendar-toolbar">
        <button
          className="icon-button"
          aria-label="Előző hét"
          onClick={() => setMonday(addCalendarDays(monday, -7))}
        >
          <ChevronLeft size={19} />
        </button>
        <h3>
          {dateFormat.format(new Date(from))} –{" "}
          {dateFormat.format(new Date(days[6].start))}{" "}
          <span>{monday.slice(0, 4)}</span>
        </h3>
        <button
          className="icon-button"
          aria-label="Következő hét"
          onClick={() => setMonday(addCalendarDays(monday, 7))}
        >
          <ChevronRight size={19} />
        </button>
        <button
          className="button secondary"
          onClick={() => setMonday(weekStart())}
        >
          Aktuális hét
        </button>
      </div>
      {result.isPending ? (
        <p role="status" className="calendar-loading">
          Foglaltság betöltése…
        </p>
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <>
          {blocked && (
            <div className="calendar-warning" role="status">
              <strong>Az eszköz jelenleg nem igényelhető.</strong>
              {result.data.overdueLoan && (
                <p>
                  Késedelmes visszahozás miatt a későbbi, üresnek látszó
                  időszakok is zároltak a visszavételig.
                </p>
              )}
              {result.data.overdueMaintenance && (
                <p>
                  A karbantartás túllépte a tervezett végét. Lezárásáig a
                  későbbi időszakok sem igényelhetők.
                </p>
              )}
            </div>
          )}
          <div className="calendar-legend">
            <span>
              <i className="reservation-key" />
              Foglalt
            </span>
            <span>
              <i className="maintenance-key" />
              Karbantartás
            </span>
            <span>Az elbírálásra váró igények nem zárolnak.</span>
          </div>
          <div className={`calendar-week${blocked ? " calendar-blocked" : ""}`}>
            {days.map((day) => {
              const intervals = intervalsOnDay(
                result.data.blockingIntervals,
                day,
              );
              return (
                <section
                  className={`calendar-day${day.date === today ? " calendar-today" : ""}${day.date < today ? " calendar-past" : ""}`}
                  key={day.date}
                  aria-label={dateFormat.format(new Date(day.start))}
                >
                  <header>
                    <span>{weekdayFormat.format(new Date(day.start))}</span>
                    <strong>{day.date.slice(-2)}</strong>
                    {day.date === today && <small>Ma</small>}
                  </header>
                  <div className="day-track" aria-hidden="true">
                    {intervals.map((interval, index) => (
                      <span
                        key={index}
                        className={`track-${interval.kind.toLowerCase()}`}
                        style={{
                          left: `${interval.left}%`,
                          width: `${interval.width}%`,
                        }}
                      />
                    ))}
                  </div>
                  {intervals.length ? (
                    <ul>
                      {intervals.map((interval, index) => (
                        <li
                          key={index}
                          className={`calendar-interval interval-${interval.kind.toLowerCase()}`}
                        >
                          <strong>{interval.label}</strong>
                          <span>
                            {interval.kind === "RESERVATION"
                              ? "Foglalt"
                              : "Karbantartás"}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="day-empty">
                      {day.date < today
                        ? "Elmúlt nap"
                        : blocked
                          ? "Átmenetileg zárolt"
                          : "Nincs jelzett akadály"}
                    </p>
                  )}
                </section>
              );
            })}
          </div>
          <p className="calendar-footnote">
            Pillanatnyi állapot · Ellenőrizve:{" "}
            {formatDate(result.data.checkedAt)}. Az igénylés és a jóváhagyás új
            ellenőrzést végez. A naptár mások nevét és igénylésük célját nem
            mutatja.
          </p>
        </>
      )}
    </section>
  );
}
