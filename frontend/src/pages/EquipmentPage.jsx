import { Link, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, MapPin, Tag, Hash, Box } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { ErrorState, LoadingCards } from "../components/Feedback";
import { ReservationForm } from "../reservations/ReservationForm";
import { AvailabilityCalendar } from "../availability/AvailabilityCalendar";

export function EquipmentPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const initialPeriod = {
    startsAt: params.get("startsAt"),
    endsAt: params.get("endsAt"),
  };
  const periodKey = `${id}:${initialPeriod.startsAt}:${initialPeriod.endsAt}`;
  const backQuery = new URLSearchParams();
  if (initialPeriod.startsAt) backQuery.set("startsAt", initialPeriod.startsAt);
  if (initialPeriod.endsAt) backQuery.set("endsAt", initialPeriod.endsAt);
  const { token } = useAuth();
  const result = useQuery({
    queryKey: ["equipment", id],
    queryFn: ({ signal }) => api(`/equipment/${id}`, { token, signal }),
  });
  return (
    <main className="page">
      <Link
        to={`/eszkozok${backQuery.size ? `?${backQuery}` : ""}`}
        className="back-link"
      >
        <ArrowLeft size={17} />
        Vissza az eszközökhöz
      </Link>
      {result.isPending ? (
        <LoadingCards />
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <>
          <header className="page-heading">
            <div>
              <code className="eyebrow">{result.data.inventoryCode}</code>
              <h1>{result.data.name}</h1>
            </div>
          </header>
          <div className="detail-layout">
            <section className="equipment-facts">
              <h2>Eszközadatok</h2>
              {result.data.description && (
                <p className="detail-description">{result.data.description}</p>
              )}
              <dl className="detail-facts">
                <div>
                  <dt>
                    <Hash size={17} />
                    Leltári szám
                  </dt>
                  <dd>{result.data.inventoryCode}</dd>
                </div>
                <div>
                  <dt>
                    <Tag size={17} />
                    Kategória
                  </dt>
                  <dd>{result.data.category.name}</dd>
                </div>
                <div>
                  <dt>
                    <MapPin size={17} />
                    Átvételi hely
                  </dt>
                  <dd>
                    {result.data.location.name}
                    <small>
                      {result.data.location.building} ·{" "}
                      {result.data.location.room}
                    </small>
                  </dd>
                </div>
                <div>
                  <dt>
                    <Box size={17} />
                    Nyilvántartás
                  </dt>
                  <dd>Aktív eszköz</dd>
                </div>
              </dl>
              <a className="text-link" href="#availability-heading">
                Foglaltsági naptár megtekintése
              </a>
            </section>
            <ReservationForm
              key={periodKey}
              equipmentId={result.data.id}
              initialPeriod={initialPeriod}
            />
          </div>
          <AvailabilityCalendar
            key={periodKey}
            equipmentId={result.data.id}
            initialStart={initialPeriod.startsAt}
          />
        </>
      )}
    </main>
  );
}
