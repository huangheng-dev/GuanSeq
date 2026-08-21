"use client";

import { useEffect } from "react";

import { MaterialIcon } from "@/components/material-icon";

export default function ErrorPage({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="routeState" id="main-content">
      <span className="routeStateIcon routeStateError"><MaterialIcon name="error" size={28} /></span>
      <h1>这次没有取得完整业务数据</h1>
      <p>可能是临时请求异常。你可以重新加载当前页面，已填写但尚未提交的内容不会自动提交。</p>
      {error.digest ? <small>错误标识：{error.digest}</small> : null}
      <button className="primaryButton" onClick={() => retry()}><MaterialIcon name="refresh" size={18} />重新加载</button>
    </main>
  );
}
