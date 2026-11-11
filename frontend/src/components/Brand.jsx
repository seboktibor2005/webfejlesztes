import { Boxes } from "lucide-react";
export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">
        <Boxes size={23} strokeWidth={1.8} />
      </span>
      <span>
        Eszköz<span className="brand-weight">Pont</span>
      </span>
    </div>
  );
}
