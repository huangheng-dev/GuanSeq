import { createWebHealthStatus } from "@/lib/health-status";
import { checkGuanSeqApiHealth } from "@/services/guanseq-api-server";

export const dynamic = "force-dynamic";

export async function GET() {
  const backend = await checkGuanSeqApiHealth();
  const health = createWebHealthStatus(
    backend,
    process.env.GUANSEQ_BUILD_VERSION ?? "0.1.0-alpha.1",
  );
  return Response.json(
    health.payload,
    {
      status: health.ready ? 200 : 503,
      headers: { "Cache-Control": "no-store" },
    },
  );
}
