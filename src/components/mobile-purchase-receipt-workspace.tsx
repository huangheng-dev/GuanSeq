"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";

import type { PurchaseReceiptRecord } from "@/lib/contracts";
import { resolveMaterialScan, resolvePurchaseOrderScan } from "@/lib/mobile-receiving";
import { submitCreatePurchaseReceipt } from "@/services/purchase-receipt-client-service";
import type { PurchaseReceiptPageData } from "@/services/purchase-receipt-server-service";
import { GsButton, GsInput } from "./ui";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";

const STORAGE_KEY = "guanseq.mobile-receiving.draft.v1";

type Draft = {
  orderScan: string;
  materialScan: string;
  warehouseId: string;
  locationId: string;
  quantity: string;
  lotNumber: string;
  note: string;
  requestId: string;
};

function requestId() {
  return `mobile-receipt-${crypto.randomUUID()}`;
}

function defaultDraft(data: PurchaseReceiptPageData): Draft {
  const warehouseId = data.references.warehouses[0]?.id ?? "";
  return {
    orderScan: "",
    materialScan: "",
    warehouseId,
    locationId: data.references.locations.find((item) => item.warehouseId === warehouseId)?.id ?? "",
    quantity: "",
    lotNumber: "",
    note: "",
    requestId: "",
  };
}

function safeDraft(value: unknown, data: PurchaseReceiptPageData): Draft | null {
  if (!value || typeof value !== "object") return null;
  const candidate = value as Partial<Draft>;
  if (typeof candidate.orderScan !== "string" || typeof candidate.materialScan !== "string") return null;
  const fallback = defaultDraft(data);
  const warehouseId = data.references.warehouses.some((item) => item.id === candidate.warehouseId)
    ? candidate.warehouseId!
    : fallback.warehouseId;
  const locationId = data.references.locations.some((item) => item.id === candidate.locationId && item.warehouseId === warehouseId)
    ? candidate.locationId!
    : data.references.locations.find((item) => item.warehouseId === warehouseId)?.id ?? "";
  return {
    orderScan: candidate.orderScan,
    materialScan: candidate.materialScan,
    warehouseId,
    locationId,
    quantity: typeof candidate.quantity === "string" ? candidate.quantity : "",
    lotNumber: typeof candidate.lotNumber === "string" ? candidate.lotNumber : "",
    note: typeof candidate.note === "string" ? candidate.note : "",
    requestId: typeof candidate.requestId === "string" && candidate.requestId.startsWith("mobile-receipt-") ? candidate.requestId : "",
  };
}

export function MobilePurchaseReceiptWorkspace({ initialData }: { initialData: PurchaseReceiptPageData }) {
  const [draft, setDraft] = useState(() => defaultDraft(initialData));
  const [hydrated, setHydrated] = useState(false);
  const [online, setOnline] = useState(true);
  const [orderAttempted, setOrderAttempted] = useState(false);
  const [materialAttempted, setMaterialAttempted] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<PurchaseReceiptRecord | null>(null);
  const skipNextPersist = useRef(false);

  const orderResolution = useMemo(
    () => resolvePurchaseOrderScan(initialData.references.releasedOrders, draft.orderScan),
    [draft.orderScan, initialData.references.releasedOrders],
  );
  const materialResolution = useMemo(
    () => orderResolution.order ? resolveMaterialScan(orderResolution.order, draft.materialScan) : { line: null, error: "请先确认采购订单。" },
    [draft.materialScan, orderResolution.order],
  );
  const locations = initialData.references.locations.filter((item) => item.warehouseId === draft.warehouseId);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setOnline(navigator.onLine);
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        const restored = stored ? safeDraft(JSON.parse(stored), initialData) : null;
        const next = restored ?? defaultDraft(initialData);
        setDraft({ ...next, requestId: next.requestId || requestId() });
        setOrderAttempted(Boolean(restored?.orderScan));
        setMaterialAttempted(Boolean(restored?.materialScan));
      } catch {
        const next = defaultDraft(initialData);
        setDraft({ ...next, requestId: requestId() });
      }
      setHydrated(true);
    });
    const updateNetwork = () => setOnline(navigator.onLine);
    window.addEventListener("online", updateNetwork);
    window.addEventListener("offline", updateNetwork);
    return () => {
      active = false;
      window.removeEventListener("online", updateNetwork);
      window.removeEventListener("offline", updateNetwork);
    };
  }, [initialData]);

  useEffect(() => {
    if (!hydrated || !draft.requestId) return;
    if (skipNextPersist.current) {
      skipNextPersist.current = false;
      return;
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
  }, [draft, hydrated]);

  function update(patch: Partial<Draft>) {
    setDraft((current) => ({ ...current, ...patch }));
    setError("");
  }

  function confirmOrder(event: FormEvent) {
    event.preventDefault();
    setOrderAttempted(true);
    if (!orderResolution.order) return;
    window.setTimeout(() => document.getElementById("mobile-material-scan")?.focus(), 0);
  }

  function confirmMaterial(event: FormEvent) {
    event.preventDefault();
    setMaterialAttempted(true);
    if (!materialResolution.line) return;
    update({
      quantity: draft.quantity || String(materialResolution.line.outstandingQuantity),
      lotNumber: draft.lotNumber || `LOT-${materialResolution.line.materialCode}-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}`,
    });
    window.setTimeout(() => document.getElementById("mobile-receipt-quantity")?.focus(), 0);
  }

  function reset() {
    skipNextPersist.current = true;
    localStorage.removeItem(STORAGE_KEY);
    const next = defaultDraft(initialData);
    setDraft({ ...next, requestId: requestId() });
    setOrderAttempted(false);
    setMaterialAttempted(false);
    setSaved(null);
    setError("");
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setOrderAttempted(true);
    setMaterialAttempted(true);
    setError("");
    const order = orderResolution.order;
    const line = materialResolution.line;
    const quantity = Number(draft.quantity);
    if (!order || !line) return;
    if (!Number.isFinite(quantity) || quantity <= 0 || quantity > line.outstandingQuantity) {
      setError(`本次收货数量必须大于 0 且不超过实时未收数量 ${line.outstandingQuantity}。`);
      return;
    }
    if (!draft.warehouseId || !draft.locationId || !draft.lotNumber.trim()) {
      setError("请选择收货仓库、库位并填写批号。 ");
      return;
    }
    if (!online) {
      setError("当前离线：草稿已保存在本机，但尚未过账。恢复网络后请重新确认提交。");
      return;
    }
    setPending(true);
    try {
      const receipt = await submitCreatePurchaseReceipt({
        purchaseOrderId: order.id,
        warehouseId: draft.warehouseId,
        locationId: draft.locationId,
        note: draft.note.trim() || "移动扫码收货",
        source: "MOBILE_SCAN",
        lines: [{ orderLineId: line.id, receivedQuantity: quantity, lotNumber: draft.lotNumber.trim() }],
      }, draft.requestId);
      skipNextPersist.current = true;
      localStorage.removeItem(STORAGE_KEY);
      setSaved(receipt);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "移动收货提交失败；草稿仍保留，可稍后使用同一请求编号重试。");
    } finally {
      setPending(false);
    }
  }

  if (!initialData.references.canCreate) {
    return <div className="businessPage mobileReceivingPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23}/></span><div><h2>采购收货扫码作业</h2><p>扫码只改变输入方式，正式过账仍由采购收货权限控制。</p></div></div></header><div className="emptyState"><MaterialIcon name="lock" size={30}/><b>当前角色无权登记采购到货</b><span>请联系工作区管理员配置仓库、库存、采购经理或管理员角色。</span><Link className="secondaryButton" href="/procurement/receipts">返回到货台账</Link></div></div>;
  }

  if (!initialData.references.releasedOrders.length) {
    return <div className="businessPage mobileReceivingPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23}/></span><div><h2>采购收货扫码作业</h2><p>面向扫码枪与移动浏览器的单行快速收货。</p></div></div></header><div className="emptyState"><MaterialIcon name="inventory" size={30}/><b>没有可扫码收货的采购订单</b><span>只有已下达且仍有未收数量的订单会进入移动作业。</span><Link className="secondaryButton" href="/procurement/orders">查看采购订单</Link></div></div>;
  }

  return <div className="businessPage mobileReceivingPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23}/></span><div><h2>采购收货扫码作业</h2><p>扫描采购单与物料，在线确认后复用正式收货、IQC 和库存过账。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/procurement/receipts"><MaterialIcon name="arrow_back" size={18}/>返回到货台账</Link></div></header>
    <div className={`mobileNetworkState ${online ? "isOnline" : "isOffline"}`} role="status"><MaterialIcon name={online ? "wifi" : "wifi_off"} size={18}/><strong>{online ? "在线，可提交正式过账" : "当前离线，仅保存本机草稿"}</strong><span>请求编号 {draft.requestId || "初始化中"}</span></div>
    {saved ? <section className="mobileReceiptSuccess" aria-live="polite"><MaterialIcon name="task_alt" size={42}/><h3>{saved.receiptNumber} 已正式过账</h3><p>{saved.orderNumber} · {saved.totalReceivedQuantity} 已收货，来源记录为移动扫码。</p><div className="mobileSuccessActions">{saved.status === "PENDING_INSPECTION" ? <Link className="secondaryButton" href="/quality/incoming">前往来料检验</Link> : <Link className="secondaryButton" href="/warehouse/inventory/on-hand">查看库存</Link>}<GsButton className="primaryButton" htmlType="button" onClick={reset}>继续下一笔</GsButton></div></section> : <form className="mobileReceivingFlow" onSubmit={submit}>
      <section className="mobileScanStep"><header><span>1</span><div><h3>扫描采购单</h3><p>支持原始单号或 `PO:` 前缀条码。</p></div></header><div className="mobileScanInput"><GsInput autoFocus aria-label="扫描采购单号" value={draft.orderScan} onChange={(event) => { update({ orderScan: event.target.value, materialScan: "", quantity: "", lotNumber: "" }); setOrderAttempted(false); setMaterialAttempted(false); }} onPressEnter={confirmOrder}/><GsButton className="secondaryButton" htmlType="button" onClick={confirmOrder}>确认</GsButton></div>{orderAttempted && orderResolution.error ? <p className="formError">{orderResolution.error}</p> : null}{orderResolution.order ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{orderResolution.order.orderNumber}</strong><span>{orderResolution.order.supplierName} · 未收物料 {orderResolution.order.lines.length} 项</span></div></div> : null}</section>
      <section className={`mobileScanStep ${orderResolution.order ? "" : "isLocked"}`}><header><span>2</span><div><h3>扫描物料</h3><p>精确匹配所选订单中的物料编码。</p></div></header><div className="mobileScanInput"><GsInput id="mobile-material-scan" aria-label="扫描物料编码" disabled={!orderResolution.order} value={draft.materialScan} onChange={(event) => { update({ materialScan: event.target.value, quantity: "", lotNumber: "" }); setMaterialAttempted(false); }} onPressEnter={confirmMaterial}/><GsButton className="secondaryButton" disabled={!orderResolution.order} htmlType="button" onClick={confirmMaterial}>确认</GsButton></div>{materialAttempted && materialResolution.error ? <p className="formError">{materialResolution.error}</p> : null}{materialResolution.line ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{materialResolution.line.materialCode} · {materialResolution.line.materialName}</strong><span>未收 {materialResolution.line.outstandingQuantity} {materialResolution.line.unit} · {materialResolution.line.inspectionRequired ? "收货后进入 IQC" : "免检直接入库"}</span></div></div> : null}</section>
      <section className={`mobileScanStep ${materialResolution.line ? "" : "isLocked"}`}><header><span>3</span><div><h3>确认收货事实</h3><p>后端将重新校验数量、状态、权限和库存位置。</p></div></header><div className="formGrid two"><label className="formField"><span>本次数量<em>必填</em></span><GsInput id="mobile-receipt-quantity" aria-label="本次收货数量" type="number" min="0.000001" max={materialResolution.line?.outstandingQuantity} step="0.000001" disabled={!materialResolution.line} value={draft.quantity} onChange={(event) => update({ quantity: event.target.value })}/></label><label className="formField"><span>批号<em>必填</em></span><GsInput aria-label="收货批号" disabled={!materialResolution.line} maxLength={80} value={draft.lotNumber} onChange={(event) => update({ lotNumber: event.target.value })}/></label><label className="formField"><span>收货仓库<em>必填</em></span><RoundedSelect ariaLabel="移动收货仓库" disabled={!materialResolution.line} size="field" value={draft.warehouseId} options={initialData.references.warehouses.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} onValueChange={(warehouseId) => update({ warehouseId, locationId: initialData.references.locations.find((item) => item.warehouseId === warehouseId)?.id ?? "" })}/></label><label className="formField"><span>收货库位<em>必填</em></span><RoundedSelect ariaLabel="移动收货库位" disabled={!materialResolution.line} size="field" value={draft.locationId} options={locations.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} onValueChange={(locationId) => update({ locationId })}/></label><label className="formField formFieldFull"><span>备注</span><GsInput aria-label="移动收货备注" disabled={!materialResolution.line} maxLength={500} value={draft.note} onChange={(event) => update({ note: event.target.value })}/></label></div>{error ? <p className="formError" role="alert">{error}</p> : null}<footer className="mobileReceivingActions"><GsButton className="secondaryButton" htmlType="button" disabled={pending} onClick={reset}>清空草稿</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={!materialResolution.line || pending || !hydrated}>{pending ? "正在过账..." : online ? "确认并正式过账" : "离线，不能过账"}</GsButton></footer></section>
    </form>}
    <div className="ledgerInsight"><MaterialIcon name="verified_user" size={18}/>扫描不等于收货：只有后端返回正式收货单后，库存和来料检验事实才会生效。</div>
  </div>;
}
