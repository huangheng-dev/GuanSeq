import { ManufacturingWorkspace } from "@/components/manufacturing-workspace";
import { getGlobalSearchIndex, getManufacturingSnapshot } from "@/services/manufacturing-service";

export default async function HomePage() {
  const [snapshot, searchIndex] = await Promise.all([getManufacturingSnapshot(), getGlobalSearchIndex()]);
  return <ManufacturingWorkspace initialSnapshot={snapshot} initialSearchIndex={searchIndex} />;
}
