import Link from "next/link";

import { getSecurityMode } from "@/lib/security-mode";
import { sanitizeReturnTo } from "@/lib/secure-cookie";

type LoginPageProps = {
  searchParams: Promise<{ returnTo?: string; error?: string; signedOut?: string }>;
};

export const dynamic = "force-dynamic";

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const query = await searchParams;
  const returnTo = sanitizeReturnTo(query.returnTo);
  const mode = getSecurityMode();
  const loginHref = mode === "oidc"
    ? `/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`
    : mode === "development"
      ? returnTo
      : "#";
  return (
    <main className="signedOutScreen" id="main-content">
      <section aria-labelledby="login-title">
        <span className="material-symbols-rounded" aria-hidden="true">lock</span>
        <h1 id="login-title">进入贯序制造工作台</h1>
        <p>
          {mode === "oidc"
            ? "请使用企业统一身份登录。贯序仍按内部账号、工作区和角色决定可访问范围。"
            : mode === "development"
              ? "当前为本地开发身份模式，可直接返回工作台。"
              : "当前部署尚未启用身份认证，请联系系统管理员完成配置。"}
        </p>
        {query.error ? <p className="formError" role="alert">登录校验失败或流程已经过期，请重新发起登录。</p> : null}
        {query.signedOut ? <p role="status">当前工作台会话已安全退出。</p> : null}
        {mode !== "disabled" ? <Link className="primaryButton" href={loginHref}>企业身份登录</Link> : null}
      </section>
    </main>
  );
}
