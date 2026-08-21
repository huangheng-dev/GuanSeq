import { MaterialIcon } from "@/components/material-icon";

export default function Loading() {
  return (
    <main className="routeState" aria-busy="true" aria-live="polite">
      <span className="routeStateIcon routeStateLoading"><MaterialIcon name="progress_activity" size={28} /></span>
      <h1>正在加载业务数据</h1>
      <p>正在校验页面模型、工作区与业务状态，请稍候。</p>
      <div className="routeStateSkeleton"><i /><i /><i /></div>
    </main>
  );
}
