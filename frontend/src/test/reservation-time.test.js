import { describe, it, expect } from "vitest";
import { fromBudapestInput, toBudapestInput } from "../reservations/format";

describe("budapesti foglalási időpontok", () => {
  it("télen és nyáron a budapesti eltéréssel küldi az időpontot", () => {
    expect(fromBudapestInput("2026-11-12T09:00")).toBe(
      "2026-11-12T08:00:00.000Z",
    );
    expect(fromBudapestInput("2026-07-12T09:00")).toBe(
      "2026-07-12T07:00:00.000Z",
    );
    expect(toBudapestInput(new Date("2026-11-12T08:00:00Z"))).toBe(
      "2026-11-12T09:00",
    );
  });
  it("nem engedi a nem létező tavaszi és a kétértelmű őszi időpontot", () => {
    expect(() => fromBudapestInput("2026-03-29T02:30")).toThrow("óraátállítás");
    expect(() => fromBudapestInput("2026-10-25T02:30")).toThrow("óraátállítás");
  });
  it("elutasítja a hibás és túlcsorduló dátumot", () => {
    expect(() => fromBudapestInput("hibás dátum")).toThrow();
    expect(() => fromBudapestInput("2026-02-30T12:00")).toThrow();
  });
});
