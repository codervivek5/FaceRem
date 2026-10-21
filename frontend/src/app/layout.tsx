import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "FaceAttend — facial attendance for modern teams",
  description:
    "Multi-tenant facial attendance: enroll faces once, check in with a glance, and keep shift-aware records per organization.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
