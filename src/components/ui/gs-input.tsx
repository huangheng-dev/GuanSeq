"use client";

import { Input, type InputProps } from "antd";
import type { TextAreaProps } from "antd/es/input";

export function GsInput(props: InputProps) {
  return <Input {...props} />;
}

export function GsTextArea(props: TextAreaProps) {
  return <Input.TextArea {...props} />;
}
