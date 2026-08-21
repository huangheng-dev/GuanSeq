"use client";

import { Select } from "antd";
import { useState } from "react";

import { MaterialIcon } from "./material-icon";

type RoundedSelectOption = string | { value: string; label: string };

type RoundedSelectProps = {
  ariaLabel: string;
  options: RoundedSelectOption[];
  optionBadges?: Record<string, number>;
  value?: string;
  defaultValue?: string;
  placeholder?: string;
  name?: string;
  disabled?: boolean;
  size?: "field" | "filter" | "compact";
  onValueChange?: (value: string) => void;
};

export function RoundedSelect({
  ariaLabel,
  options,
  optionBadges,
  value,
  defaultValue = "",
  placeholder = "请选择",
  name,
  disabled = false,
  size = "filter",
  onValueChange,
}: RoundedSelectProps) {
  const [internalValue, setInternalValue] = useState(defaultValue);
  const selectedValue = value ?? internalValue;
  const normalizedOptions = options.map((option) => typeof option === "string" ? { value: option, label: option } : option);

  function choose(nextValue: string) {
    if (value === undefined) setInternalValue(nextValue);
    onValueChange?.(nextValue);
  }

  return (
    <div className={`roundedSelect roundedSelect${size}`}>
      {name ? <input type="hidden" name={name} value={selectedValue} /> : null}
      <Select<string>
        aria-label={ariaLabel}
        className={`gsSelect gsSelect${size}`}
        classNames={{ popup: { root: "gsSelectPopup" } }}
        disabled={disabled}
        menuItemSelectedIcon={null}
        onChange={choose}
        optionRender={(option) => {
          const label = String(option.data.label ?? option.data.value);
          const selected = option.data.value === selectedValue;
          return (
            <span className="gsSelectOption">
              <span className="gsSelectOptionLabel">{label}</span>
              <span className="gsSelectOptionTrailing">
                <span className="gsSelectCheckSlot">{selected ? <MaterialIcon name="check" size={16} /> : null}</span>
                {optionBadges?.[label] !== undefined ? <span className="gsSelectBadge" aria-label={`${optionBadges[label]} 条`}>{optionBadges[label]}</span> : null}
              </span>
            </span>
          );
        }}
        options={normalizedOptions}
        placeholder={placeholder}
        popupMatchSelectWidth={optionBadges ? 190 : size === "compact" ? false : undefined}
        showSearch={normalizedOptions.length > 8 ? { optionFilterProp: "label" } : false}
        size={size === "field" ? "large" : size === "compact" ? "small" : "middle"}
        value={selectedValue || undefined}
      />
    </div>
  );
}
