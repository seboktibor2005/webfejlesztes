import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, MapPin, Tag, Hash, Box } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { EquipmentArt } from "../components/EquipmentArt";
import { ErrorState, LoadingCards } from "../components/Feedback";
import { ReservationForm } from "../reservations/ReservationForm";

export function EquipmentPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const result = useQuery({
    queryKey: ["equipment", id],
    queryFn: ({ signal }) => api(`/equipment/${id}`, { token, signal }),
  });
  return (
    <main className="page">
      <Link to="/eszkozok" className="back-link">
        <ArrowLeft size={17} />
        Vissza az eszközökhöz
      </Link>
      {result.isPending ? (
        <LoadingCards />
      ) : result.isError ? (
        <ErrorState error={result.error} retry={result.refetch} />
      ) : (
        <div className="detail-layout">
          <EquipmentArt code={result.data.inventoryCode} large />
          <section>
            <span className="eyebrow">{result.data.category.name}</span>
            <h1>{result.data.name}</h1>
            <p className="detail-description">{result.data.description}</p>
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
            <div className="detail-note">
              Az eszköz leltári állapota és az adott időszakra vonatkozó
              foglalhatósága eltérhet.
            </div>
            <ReservationForm equipmentId={result.data.id} />
          </section>
        </div>
      )}
    </main>
  );
}
