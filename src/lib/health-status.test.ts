import { describe, expect, it } from "vitest";

import { createWebHealthStatus } from "./health-status";

describe("production readiness status", () => {
  it("is ready only when the backend public status contract is healthy", () => {
    expect(createWebHealthStatus({
      service: "guanseq-server",
      status: "UP",
      version: "0.1.0-rc.1",
    }, "0.1.0-rc.1")).toEqual({
      ready: true,
      payload: {
        service: "guanseq-web",
        status: "UP",
        version: "0.1.0-rc.1",
        backend: {
          service: "guanseq-server",
          status: "UP",
          version: "0.1.0-rc.1",
        },
      },
    });
  });

  it("reports a degraded web service without exposing an exception", () => {
    expect(createWebHealthStatus(null, "0.1.0-rc.1")).toMatchObject({
      ready: false,
      payload: {
        service: "guanseq-web",
        status: "DEGRADED",
        backend: { status: "UNAVAILABLE" },
      },
    });
  });
});
