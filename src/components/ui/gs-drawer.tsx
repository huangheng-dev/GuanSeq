"use client";

import { Drawer, type DrawerProps } from "antd";

type GsDrawerProps = Omit<DrawerProps, "width"> & { ariaLabel?: string };

export function GsDrawer({ destroyOnHidden = true, placement = "right", size = 720,
  closable = { placement: "end" }, ariaLabel, ...props }: GsDrawerProps) {
  return <Drawer aria-label={ariaLabel} destroyOnHidden={destroyOnHidden} placement={placement} size={size} closable={closable} {...props} />;
}
