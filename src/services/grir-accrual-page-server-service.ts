import "server-only";

import { listGrirAccruals } from "./grir-accrual-server-service";

export type GrirAccrualPageData = {
  source: "backend";
  year: number;
  page: Awaited<ReturnType<typeof listGrirAccruals>>;
};

export async function getGrirAccrualPageData(
  pathname: string,
): Promise<GrirAccrualPageData | null> {
  if (pathname !== "/finance/purchase-settlement/grir-accruals") return null;
  const year = new Date().getFullYear();
  const page = await listGrirAccruals({ year, status: "ALL", page: 0, size: 20 });
  return { source: "backend", year, page };
}
