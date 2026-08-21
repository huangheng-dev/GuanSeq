import Link from "next/link";

import { MaterialIcon } from "@/components/material-icon";

export default function NotFound() {
  return (
    <main className="routeState" id="main-content">
      <span className="routeStateIcon"><MaterialIcon name="travel_explore" size={28} /></span>
      <h1>没有找到对应的业务栏目</h1>
      <p>地址可能已经变化，或者当前工作区尚未开放这项能力。</p>
      <Link className="primaryButton" href="/"><MaterialIcon name="home" size={18} />返回经营总览</Link>
    </main>
  );
}
