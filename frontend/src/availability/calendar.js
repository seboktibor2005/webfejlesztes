import { fromBudapestInput, toBudapestInput } from "../reservations/format";

export function addCalendarDays(day, count) {
  const [year, month, date] = day.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, date + count))
    .toISOString()
    .slice(0, 10);
}

export function weekStart(value) {
  const instant = value ? new Date(value) : new Date();
  const day = toBudapestInput(
    Number.isNaN(instant.getTime()) ? new Date() : instant,
  ).slice(0, 10);
  const weekday = new Date(`${day}T12:00:00Z`).getUTCDay();
  return addCalendarDays(day, -((weekday + 6) % 7));
}

export function weekDays(monday) {
  return Array.from({ length: 7 }, (_, index) => {
    const date = addCalendarDays(monday, index);
    return {
      date,
      start: fromBudapestInput(`${date}T00:00`),
      end: fromBudapestInput(`${addCalendarDays(date, 1)}T00:00`),
    };
  });
}

export function intervalsOnDay(intervals, day) {
  const start = Date.parse(day.start);
  const end = Date.parse(day.end);
  return intervals
    .filter(
      (interval) =>
        Date.parse(interval.startsAt) < end &&
        Date.parse(interval.endsAt) > start,
    )
    .map((interval) => {
      const clippedStart = Math.max(Date.parse(interval.startsAt), start);
      const clippedEnd = Math.min(Date.parse(interval.endsAt), end);
      const time = (stamp) => toBudapestInput(new Date(stamp)).slice(11);
      return {
        ...interval,
        label: `${time(clippedStart)}–${clippedEnd === end ? "24:00" : time(clippedEnd)}`,
        left: ((clippedStart - start) / (end - start)) * 100,
        width: ((clippedEnd - clippedStart) / (end - start)) * 100,
      };
    });
}

export function inputPeriod(period) {
  const input = (value) => {
    const instant = new Date(value);
    return value && !Number.isNaN(instant.getTime())
      ? toBudapestInput(instant)
      : "";
  };
  return { startsAt: input(period?.startsAt), endsAt: input(period?.endsAt) };
}
