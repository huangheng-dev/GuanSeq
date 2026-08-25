"use client";

import { Children, cloneElement, isValidElement, type ReactElement, type ReactNode } from "react";
import { GsDrawer } from "./gs-drawer";
import { GsModal } from "./gs-modal";

function withoutNestedDialogRole(children: ReactNode) {
  return Children.map(children, (child) => isValidElement(child)
    ? cloneElement(child as ReactElement<Record<string, unknown>>, { role: undefined, "aria-modal": undefined })
    : child);
}

export function GsModalHost({ children, onClose, zIndex }: { children: ReactNode; onClose: () => void; zIndex?: number }) {
  return <GsModal className="gsModalHost" open closable={false} footer={null} onCancel={onClose} zIndex={zIndex}>{withoutNestedDialogRole(children)}</GsModal>;
}

export function GsDrawerHost({ children, onClose, size }: { children: ReactNode; onClose: () => void; size?: number | string }) {
  return <GsDrawer className="gsDrawerHost" open closable={false} size={size} onClose={onClose}>{withoutNestedDialogRole(children)}</GsDrawer>;
}
