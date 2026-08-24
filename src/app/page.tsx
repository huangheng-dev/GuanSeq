import { ManufacturingWorkspace } from "@/components/manufacturing-workspace";
import { getGlobalSearchIndex, getManufacturingSnapshot } from "@/services/manufacturing-service";
import { requireFrontendSession } from "@/services/oidc-session-server";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  await requireFrontendSession("/");
  const [snapshot, searchIndex] = await Promise.all([getManufacturingSnapshot(), getGlobalSearchIndex()]);
  return <ManufacturingWorkspace initialSnapshot={snapshot} initialSearchIndex={searchIndex} />;
}
