import type { OperationTaskRecord, ProductionOrderRecord } from "@/lib/contracts";

function canonical(value: string, prefix: "OT" | "EMP") {
  return value.trim().replace(new RegExp(`^${prefix}[:|]`, "i"), "").trim();
}

export function resolveMobileOperationTaskScan(tasks: OperationTaskRecord[], scannedValue: string) {
  const value = canonical(scannedValue, "OT").toLocaleUpperCase("en-US");
  if (!value) return { task: null, error: "请扫描或输入工序任务标签。" };
  const matches = tasks.filter((task) => task.id.toLocaleUpperCase("en-US") === value
    || task.taskNumber.toLocaleUpperCase("en-US") === value);
  return matches.length === 1
    ? { task: matches[0], error: "" }
    : { task: null, error: matches.length > 1 ? "工序任务标签不唯一，请扫描任务 UUID 标签。" : "未找到当前工作区的工序任务。" };
}

export function resolveMobileOperatorScan(scannedValue: string, currentUsername: string) {
  const value = canonical(scannedValue, "EMP");
  if (!value) return { username: null, error: "请扫描当前登录人员标签。" };
  return value.toLocaleLowerCase("en-US") === currentUsername.toLocaleLowerCase("en-US")
    ? { username: currentUsername, error: "" }
    : { username: null, error: "人员标签与当前登录账号不一致，不能代替他人执行或报工。" };
}

export type MobileReportingAction = "START" | "COMPLETE" | "REPORT" | "WAIT" | "DONE";

export function deriveMobileReportingAction(
  task: OperationTaskRecord,
  orders: ProductionOrderRecord[],
  tasks: OperationTaskRecord[],
): { action: MobileReportingAction; order: ProductionOrderRecord | null; error: string } {
  const order = orders.find((item) => item.id === task.orderId) ?? null;
  if (!order) return { action: "WAIT", order: null, error: "工序关联的生产订单不可用，请刷新后重试。" };
  if (!["RELEASED", "IN_PROGRESS"].includes(order.status)) {
    return { action: order.status === "COMPLETED" ? "DONE" : "WAIT", order,
      error: order.status === "COMPLETED" ? "该生产订单已经完成。" : "当前生产订单状态不允许执行扫码报工。" };
  }
  if (task.status === "PENDING") return { action: "START", order, error: "" };
  if (task.status === "IN_PROGRESS") return { action: "COMPLETE", order, error: "" };
  const orderTasks = tasks.filter((item) => item.orderId === order.id);
  if (orderTasks.some((item) => item.status !== "COMPLETED")) {
    return { action: "WAIT", order, error: "该工序已完工；仍有其他工序未完成，请扫描下一道待执行任务。" };
  }
  const finalTask = [...orderTasks].sort((left, right) => right.sequenceNumber - left.sequenceNumber)[0];
  if (finalTask?.id !== task.id) return { action: "WAIT", order, error: "全部工序已完成，请扫描最后一道工序标签提交生产报工。" };
  if (order.reportableQuantity <= 0) return { action: "DONE", order, error: "当前订单没有可报数量，可能已有在检报工。" };
  return { action: "REPORT", order, error: "" };
}
