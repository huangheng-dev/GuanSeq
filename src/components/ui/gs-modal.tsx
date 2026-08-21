"use client";

import { Modal, type ModalProps } from "antd";

type GsModalProps = Omit<ModalProps, "maskClosable">;

export function GsModal({ centered = true, destroyOnHidden = true, mask, ...props }: GsModalProps) {
  const normalizedMask = mask === false ? false : typeof mask === "object" ? { closable: false, ...mask } : { closable: false };
  return <Modal centered={centered} destroyOnHidden={destroyOnHidden} mask={normalizedMask} {...props} />;
}
