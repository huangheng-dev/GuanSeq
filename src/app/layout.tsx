import "@fontsource-variable/noto-sans-sc";
import "material-symbols/rounded.css";
import "./globals.css";

import { AntdRegistry } from "@ant-design/nextjs-registry";
import type { Metadata } from "next";
import type { ReactNode } from "react";

import { GuanSeqUIProvider } from "@/components/ui";
import { brandIdentity } from "@/lib/brand-identity";

export const metadata: Metadata = {
  title: `${brandIdentity.name} · ${brandIdentity.productTitle}`,
  description: brandIdentity.description,
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <GuanSeqUIProvider>
            <a className="skipLink" href="#main-content">跳转到主要内容</a>
            {children}
          </GuanSeqUIProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
