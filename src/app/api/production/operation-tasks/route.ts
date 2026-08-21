import { fetchOperationTaskPageData } from "@/services/operation-task-server-service";

export async function GET() {
  const data = await fetchOperationTaskPageData();
  return Response.json(data, { headers: { "X-Request-Id": crypto.randomUUID() } });
}
