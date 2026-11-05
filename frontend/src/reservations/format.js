export const reservationLabels = {
  REQUESTED: "Elbírálásra vár",
  APPROVED: "Jóváhagyva",
  REJECTED: "Elutasítva",
  CANCELLED: "Visszavonva",
  EXPIRED: "Lejárt",
  COMPLETED: "Lezárva",
};

const inputFormatter = new Intl.DateTimeFormat("sv-SE", {
  timeZone: "Europe/Budapest",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
export function toBudapestInput(date) {
  const parts = Object.fromEntries(
    inputFormatter.formatToParts(date).map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function fromBudapestInput(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))
    throw new Error("Érvényes időpontot adj meg.");
  const [year, month, day, hour, minute] = value.match(/\d+/g).map(Number);
  const localStamp = Date.UTC(year, month - 1, day, hour, minute);
  // Budapest a foglalási időhorizonton UTC+1 vagy UTC+2. Visszaellenőrzéssel a DST-rés és a kettős óra is felismerhető.
  const candidates = [60, 120]
    .map((offset) => new Date(localStamp - offset * 60_000))
    .filter((date) => toBudapestInput(date) === value);
  if (candidates.length !== 1)
    throw new Error(
      "Ez az időpont az óraátállítás miatt nem egyértelmű vagy nem létezik. Válassz másik időpontot.",
    );
  return candidates[0].toISOString();
}

export function formatDate(value) {
  return new Intl.DateTimeFormat("hu-HU", {
    timeZone: "Europe/Budapest",
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
