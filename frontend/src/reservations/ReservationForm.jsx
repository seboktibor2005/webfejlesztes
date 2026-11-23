import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { CalendarPlus, ArrowRight } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { fromBudapestInput, toBudapestInput } from "./format";
import { inputPeriod } from "../availability/calendar";

export function ReservationForm({ equipmentId, initialPeriod }) {
  const { token } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const tomorrow = toBudapestInput(new Date(Date.now() + 86_400_000)).slice(
    0,
    10,
  );
  const supplied = inputPeriod(initialPeriod);
  const [values, setValues] = useState({
    startsAt: supplied.startsAt || `${tomorrow}T09:00`,
    endsAt: supplied.endsAt || `${tomorrow}T12:00`,
    purpose: "",
  });
  const mutation = useMutation({
    mutationFn: () =>
      api("/reservations", {
        token,
        method: "POST",
        body: {
          equipmentId,
          startsAt: fromBudapestInput(values.startsAt),
          endsAt: fromBudapestInput(values.endsAt),
          purpose: values.purpose.trim(),
        },
      }),
    onSuccess: (reservation) => {
      client.invalidateQueries({ queryKey: ["reservations"] });
      client.invalidateQueries({ queryKey: ["availability", equipmentId] });
      client.invalidateQueries({ queryKey: ["equipment"] });
      navigate(`/foglalasok/${reservation.id}`, { state: { created: true } });
    },
  });
  const errorFor = (name) =>
    mutation.error?.errors?.find((error) => error.field === name)?.message;
  function update(event) {
    const { name, value } = event.target;
    setValues((previous) => ({ ...previous, [name]: value }));
    mutation.reset();
  }
  return (
    <section
      className="reservation-form-panel"
      aria-labelledby="reservation-form-heading"
    >
      <div className="form-section-heading">
        <CalendarPlus size={21} />
        <h2 id="reservation-form-heading">Foglalási igény</h2>
      </div>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate();
        }}
        className="reservation-form"
      >
        <fieldset disabled={mutation.isPending}>
          <div className="form-columns">
            <label>
              Átvétel időpontja
              <input
                type="datetime-local"
                name="startsAt"
                value={values.startsAt}
                onChange={update}
                onInput={update}
                required
                step="60"
                aria-invalid={!!errorFor("startsAt")}
              />
              {errorFor("startsAt") && (
                <small className="field-error">{errorFor("startsAt")}</small>
              )}
            </label>
            <label>
              Visszahozás időpontja
              <input
                type="datetime-local"
                name="endsAt"
                value={values.endsAt}
                onChange={update}
                onInput={update}
                required
                step="60"
                aria-invalid={!!errorFor("endsAt")}
              />
              {errorFor("endsAt") && (
                <small className="field-error">{errorFor("endsAt")}</small>
              )}
            </label>
          </div>
          <small className="input-help">
            Budapesti idő szerint · 1 óra–14 nap · legfeljebb 60 nappal előre.
          </small>
          <label>
            Mire használnád az eszközt?
            <textarea
              name="purpose"
              value={values.purpose}
              onChange={update}
              required
              minLength={1}
              maxLength={500}
              rows={3}
              placeholder="Például interjúfelvétel a szemináriumi projekthez"
              aria-invalid={!!errorFor("purpose")}
            />
            {errorFor("purpose") && (
              <small className="field-error">{errorFor("purpose")}</small>
            )}
          </label>
          {mutation.isError && (
            <div className="form-error" role="alert">
              {mutation.error.message}
            </div>
          )}
          <button
            className="button primary"
            type="submit"
            disabled={mutation.isPending}
          >
            {mutation.isPending
              ? "Igénylés küldése…"
              : "Foglalási igény küldése"}
            <ArrowRight size={17} />
          </button>
        </fieldset>
      </form>
    </section>
  );
}
