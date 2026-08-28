import type { NextConfig } from "next";

const deploymentId = process.env.GUANSEQ_DEPLOYMENT_ID?.replace(/[^A-Za-z0-9_-]/g, "-");

const nextConfig: NextConfig = {
  deploymentId,
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  devIndicators: false,
};

export default nextConfig;
