"use client";

import { Checkbox } from "antd";
import type { CheckboxChangeEvent } from "antd/es/checkbox";
import type { ChangeEvent, InputHTMLAttributes } from "react";

type ControlledCheckboxProps = {
  ariaLabel: string;
  checked: boolean;
  className?: string;
  disabled?: boolean;
  indeterminate?: boolean;
  onCheckedChange: (checked: boolean) => void;
};

type NativeCheckboxProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
  ariaLabel?: never;
  indeterminate?: boolean;
  onCheckedChange?: never;
};

export function GsCheckbox(props: ControlledCheckboxProps | NativeCheckboxProps) {
  if ("onCheckedChange" in props && typeof props.onCheckedChange === "function") {
    const { ariaLabel, checked, className, disabled = false, indeterminate = false, onCheckedChange } = props;
    return <Checkbox aria-label={ariaLabel} checked={checked} className={className} disabled={disabled} indeterminate={indeterminate} onClick={(event) => event.stopPropagation()} onChange={(event) => onCheckedChange(event.target.checked)} />;
  }

  const { checked, className, defaultChecked, disabled, id, name, onChange, onClick, title, ...rest } = props as NativeCheckboxProps;
  return (
    <Checkbox
      {...rest}
      checked={checked}
      className={className}
      defaultChecked={defaultChecked}
      disabled={disabled}
      id={id}
      name={name}
      title={title}
      onClick={onClick}
      onChange={(event: CheckboxChangeEvent) => onChange?.(event as unknown as ChangeEvent<HTMLInputElement>)}
    />
  );
}
