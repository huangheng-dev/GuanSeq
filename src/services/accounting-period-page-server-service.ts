import "server-only";

import { getAccountingPeriods } from "./accounting-period-server-service";

export type AccountingPeriodPageData = {
  source: "backend";
  year: number;
  periods: Awaited<ReturnType<typeof getAccountingPeriods>>;
};

export async function getAccountingPeriodPageData(
  pathname: string,
): Promise<AccountingPeriodPageData | null> {
  if (pathname !== "/finance/accounting-periods") return null;
  const year = new Date().getFullYear();
  const periods = await getAccountingPeriods(year);
  return { source: "backend", year, periods };
}
