FROM node:24.18.0-bookworm-slim AS base
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@11.16.0 --activate
WORKDIR /workspace

FROM base AS dependencies
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
    pnpm install --frozen-lockfile

FROM base AS builder
ARG GUANSEQ_BUILD_VERSION=0.1.0-alpha.1
ENV NEXT_TELEMETRY_DISABLED=1
ENV GUANSEQ_BUILD_VERSION=$GUANSEQ_BUILD_VERSION
ENV GUANSEQ_DEPLOYMENT_ID=$GUANSEQ_BUILD_VERSION
COPY --from=dependencies /workspace/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM node:24.18.0-bookworm-slim AS runtime
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN groupadd --system --gid 10001 guanseq \
    && useradd --system --uid 10001 --gid guanseq --home-dir /nonexistent --shell /usr/sbin/nologin guanseq \
    && rm -rf /usr/local/lib/node_modules/npm \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx
WORKDIR /app
COPY --from=builder --chown=guanseq:guanseq /workspace/.next/standalone ./
COPY --from=builder --chown=guanseq:guanseq /workspace/.next/static ./.next/static
USER guanseq
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=4s --start-period=30s --retries=6 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:3000/api/health',{signal:AbortSignal.timeout(3000)}).then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"]
CMD ["node", "server.js"]
