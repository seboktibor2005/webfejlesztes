export function EquipmentArt({ code, large = false }) {
  const kind = code.split("-")[0];
  return (
    <div
      className={`equipment-art art-${kind.toLowerCase()} ${large ? "art-large" : ""}`}
      aria-hidden="true"
    >
      <div className="art-circle" />
      <svg viewBox="0 0 300 180" fill="none">
        <ellipse
          cx="155"
          cy="148"
          rx="76"
          ry="9"
          fill="currentColor"
          opacity=".08"
        />
        {kind === "FOTO" && (
          <g>
            <path
              d="M77 66h40l10-16h43l12 16h40a8 8 0 0 1 8 8v57a8 8 0 0 1-8 8H77a8 8 0 0 1-8-8V74a8 8 0 0 1 8-8Z"
              fill="#334a49"
            />
            <path
              d="M73 92h153v38a5 5 0 0 1-5 5H78a5 5 0 0 1-5-5Z"
              fill="#203c3b"
            />
            <circle cx="153" cy="103" r="39" fill="#637f76" />
            <circle cx="153" cy="103" r="32" fill="#193734" />
            <circle cx="153" cy="103" r="24" fill="#365e55" />
            <circle cx="153" cy="103" r="17" fill="#102b2b" />
            <circle cx="145" cy="96" r="7" fill="#a3c4ac" opacity=".5" />
            <rect x="191" y="77" width="22" height="10" rx="2" fill="#dae6d6" />
            <rect x="87" y="59" width="20" height="7" rx="2" fill="#637f76" />
          </g>
        )}
        {kind === "HANG" && (
          <g transform="rotate(-12 150 90)">
            <rect
              x="121"
              y="30"
              width="57"
              height="82"
              rx="28"
              fill="#4d565e"
            />
            <path
              d="M130 48h39M126 60h46M125 72h48M128 84h42"
              stroke="#a9b0ae"
              strokeWidth="3"
            />
            <path
              d="M109 87v11c0 24 18 39 41 39s41-15 41-39V87M150 138v17M126 157h48"
              stroke="#353e48"
              strokeWidth="7"
              strokeLinecap="round"
            />
            <rect
              x="139"
              y="108"
              width="22"
              height="12"
              rx="3"
              fill="#343d45"
            />
          </g>
        )}
        {kind === "PREZ" && (
          <g>
            <path d="m70 84 137-19 25 27-4 46-147 9-16-22Z" fill="#c8c3ab" />
            <path d="m70 84 141-9 19 17-145 10Z" fill="#f2f0e4" />
            <path d="m85 102 145-10-2 46-147 9Z" fill="#e2dfcd" />
            <ellipse cx="183" cy="119" rx="23" ry="18" fill="#7e8275" />
            <ellipse cx="183" cy="119" rx="17" ry="14" fill="#263b36" />
            <ellipse cx="179" cy="116" rx="8" ry="7" fill="#8cab94" />
            <path
              d="m99 116 46-3m-46 10 46-3m-46 10 46-3"
              stroke="#b3b29d"
              strokeWidth="3"
            />
            <path d="m96 86 56-3" stroke="#b3b29d" strokeWidth="3" />
          </g>
        )}
        {kind === "FEJL" && (
          <g transform="rotate(-12 150 90)">
            <rect x="87" y="49" width="128" height="94" rx="7" fill="#31867a" />
            <rect x="126" y="76" width="50" height="43" rx="3" fill="#263e3a" />
            <path d="M135 83h33v29h-33Z" stroke="#72988a" />
            <path
              d="M100 61h101M101 132h100"
              stroke="#202f30"
              strokeWidth="10"
              strokeDasharray="4 4"
            />
            <rect x="74" y="75" width="24" height="28" rx="2" fill="#c7cbc0" />
            <rect x="75" y="116" width="23" height="16" rx="2" fill="#2c3837" />
            <circle cx="197" cy="88" r="7" fill="#dad5b0" />
            <circle cx="197" cy="110" r="5" fill="#d4e6ba" />
            <path
              d="M114 87h-8v34h18m58-22h10v24h13"
              stroke="#79b7a2"
              strokeWidth="2"
            />
          </g>
        )}
      </svg>
      <span className="art-code">{code}</span>
    </div>
  );
}
