"use client";

import { type FormEvent, useState } from "react";

import { saveUserProfile, type UserProfile } from "@/services/front-end-product-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost } from "./ui";

export function ProfileDialog({ profile, onClose, onSaved }: { profile: UserProfile; onClose: () => void; onSaved: (profile: UserProfile) => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");
    const values = Object.fromEntries(new FormData(event.currentTarget).entries()) as Record<string, string>;
    try {
      const saved = await saveUserProfile({
        name: values.name ?? "",
        title: values.title ?? "",
        department: values.department ?? "",
        email: values.email ?? "",
        phone: values.phone ?? "",
        locale: values.locale ?? profile.locale,
        notificationPreference: values.notificationPreference ?? profile.notificationPreference,
      });
      onSaved(saved);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "个人资料保存失败，请重试。");
      setPending(false);
    }
  }

  return (
    <GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="accountDialog" role="dialog" aria-modal="true" aria-labelledby="profile-dialog-title">
        <header><span><MaterialIcon name="person" size={23} /></span><div><h2 id="profile-dialog-title">个人资料</h2><p>维护当前前端工作区显示的信息和通知偏好。</p></div><GsButton intent="text" onClick={onClose} disabled={pending} aria-label="关闭个人资料"><MaterialIcon name="close" /></GsButton></header>
        <form onSubmit={submit} noValidate>
          <div className="accountFormGrid">
            <label><span>姓名<em>必填</em></span><GsInput name="name" defaultValue={profile.name} /></label>
            <label><span>职位<em>必填</em></span><GsInput name="title" defaultValue={profile.title} /></label>
            <label><span>所属部门</span><GsInput name="department" defaultValue={profile.department} /></label>
            <label><span>手机号</span><GsInput name="phone" defaultValue={profile.phone} /></label>
            <label className="accountFieldFull"><span>邮箱<em>必填</em></span><GsInput name="email" type="email" defaultValue={profile.email} /></label>
            <label><span>界面语言</span><RoundedSelect ariaLabel="界面语言" name="locale" options={["简体中文", "English（预留）"]} defaultValue={profile.locale} size="field" /></label>
            <label><span>通知偏好</span><RoundedSelect ariaLabel="通知偏好" name="notificationPreference" options={["重要业务与风险", "仅高风险", "全部业务提醒"]} defaultValue={profile.notificationPreference} size="field" /></label>
          </div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18} />{error}</div> : null}
          <footer><span><MaterialIcon name="shield" size={16} />当前保存在浏览器工作区，后续由账户服务接管</span><div><GsButton htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton intent="primary" htmlType="submit" loading={pending}>{pending ? "正在保存" : "保存资料"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>
  );
}

export function SessionExitDialog({ onClose }: { onClose: () => void }) {
  return (
    <GsModalHost onClose={onClose}>
      <section className="sessionExitDialog" role="alertdialog" aria-modal="true" aria-labelledby="session-exit-title">
        <span><MaterialIcon name="logout" size={24} /></span><div><h2 id="session-exit-title">确认退出当前工作台？</h2><p>系统将结束当前受保护会话；收藏和最近访问仍保留在本机。</p></div>
        <footer><GsButton onClick={onClose}>取消</GsButton><form action="/api/auth/logout" method="post"><GsButton intent="danger" data-session-exit="true" htmlType="submit">退出登录</GsButton></form></footer>
      </section>
    </GsModalHost>
  );
}
