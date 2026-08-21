"use client";

import { Button, type ButtonProps } from "antd";

type GsButtonIntent = "primary" | "secondary" | "text" | "danger";

export type GsButtonProps = Omit<ButtonProps, "type" | "danger"> & { intent?: GsButtonIntent };

export function GsButton({ intent, className, ...props }: GsButtonProps) {
  const resolvedIntent = intent ?? (className?.includes("primaryButton") ? "primary" : className?.includes("danger") ? "danger" : className?.includes("iconButton") ? "text" : "secondary");
  return <Button {...props} className={className} danger={resolvedIntent === "danger"} type={resolvedIntent === "primary" ? "primary" : resolvedIntent === "text" ? "text" : "default"} />;
}
