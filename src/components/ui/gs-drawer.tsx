"use client";

import { Drawer, type DrawerProps } from "antd";

type GsDrawerProps = Omit<DrawerProps, "width">;

export function GsDrawer({ destroyOnHidden = true, placement = "right", size = 720,
  closable = { placement: "end" }, ...props }: GsDrawerProps) {
  return <Drawer destroyOnHidden={destroyOnHidden} placement={placement} size={size} closable={closable} {...props} />;
}
