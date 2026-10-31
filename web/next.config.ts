import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The browser never talks to FastAPI directly: requests go through this app's route
  // handlers so the access token can live in an httpOnly cookie. That also means no CORS.
  reactStrictMode: true,
};

export default nextConfig;
