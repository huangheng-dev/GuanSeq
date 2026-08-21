"use client";

import { type RefObject, useEffect, useRef } from "react";

type OverlayFocusOptions = {
  open?: boolean;
  blocked?: boolean;
  onClose: () => void;
  preferredSelector?: string;
};

export function useOverlayFocus(
  dialogRef: RefObject<HTMLElement | null>,
  {
    open = true,
    blocked = false,
    onClose,
    preferredSelector = 'input:not([type="hidden"]):not(:disabled), textarea:not(:disabled)',
  }: OverlayFocusOptions,
) {
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const blockedRef = useRef(blocked);
  const closeRef = useRef(onClose);

  useEffect(() => {
    blockedRef.current = blocked;
  }, [blocked]);

  useEffect(() => {
    closeRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const modalRoot = dialog?.parentElement;
    const focusableSelector = 'button:not(:disabled), a[href], input:not([type="hidden"]):not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])';
    const background = modalRoot?.parentElement
      ? Array.from(modalRoot.parentElement.children).filter((element) => element !== modalRoot) as HTMLElement[]
      : [];
    const previousState = background.map((element) => ({ element, inert: element.hasAttribute("inert"), ariaHidden: element.getAttribute("aria-hidden") }));
    background.forEach((element) => { element.setAttribute("inert", ""); element.setAttribute("aria-hidden", "true"); });
    (dialog?.querySelector<HTMLElement>(preferredSelector) ?? dialog?.querySelector<HTMLElement>(focusableSelector))?.focus();

    function containFocus(event: KeyboardEvent) {
      if (event.key === "Escape" && !blockedRef.current) {
        event.preventDefault();
        closeRef.current();
        return;
      }
      if (event.key !== "Tab" || !dialog) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector)).filter((element) => !element.hasAttribute("disabled"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    window.addEventListener("keydown", containFocus);
    return () => {
      window.removeEventListener("keydown", containFocus);
      previousState.forEach(({ element, inert, ariaHidden }) => {
        if (!inert) element.removeAttribute("inert");
        if (ariaHidden === null) element.removeAttribute("aria-hidden"); else element.setAttribute("aria-hidden", ariaHidden);
      });
      returnFocusRef.current?.focus();
    };
  }, [dialogRef, open, preferredSelector]);
}
