type MaterialIconProps = {
  name: string;
  size?: number;
  filled?: boolean;
};

export function MaterialIcon({ name, size = 20, filled = false }: MaterialIconProps) {
  return (
    <span
      aria-hidden="true"
      className="material-symbols-rounded"
      style={{ fontSize: size, fontVariationSettings: `'FILL' ${filled ? 1 : 0}` }}
    >
      {name}
    </span>
  );
}

