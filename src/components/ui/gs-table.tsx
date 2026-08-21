"use client";

import { Table, type TableProps } from "antd";

export function GsTable<RecordType extends object>({ pagination = false, size = "middle", ...props }: TableProps<RecordType>) {
  return <Table<RecordType> pagination={pagination} size={size} {...props} />;
}
