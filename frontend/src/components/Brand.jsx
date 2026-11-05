import { Boxes } from "lucide-react";
export function Brand({ light = false }) {
  return (
    <div className={`brand ${light ? "brand-light" : ""}`}>
      <span className="brand-mark">
        <Boxes size={23} strokeWidth={1.8} />
      </span>
      <span>
        Eszköz<span className="brand-weight">Pont</span>
        <span className="brand-dot">.</span>
      </span>
    </div>
  );
}
