"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";

import {
  askManufacturingAssistant,
  type AiAssistantContext,
  type AiAssistantResponse,
} from "@/services/ai-assistant-service";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsDrawerHost, GsTextArea } from "./ui";

type AiAssistantProps = {
  open: boolean;
  context: AiAssistantContext;
  userName: string;
  onClose: () => void;
};

type AiMessage = {
  id: string;
  role: "user" | "assistant";
  question?: string;
  response?: AiAssistantResponse;
  pending?: boolean;
  error?: string;
};

const quickPrompts = ["分析当前风险", "生成今日经营摘要", "给出计划与产能建议"];

export function AiAssistant({ open, context, userName, onClose }: AiAssistantProps) {
  const feedRef = useRef<HTMLDivElement>(null);
  const requestSequenceRef = useRef(0);
  const [question, setQuestion] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [messages, setMessages] = useState<AiMessage[]>([]);

  useEffect(() => {
    if (!open) return;
    feedRef.current?.scrollTo({ top: feedRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, open]);

  async function submitQuestion(nextQuestion = question) {
    const normalizedQuestion = nextQuestion.trim();
    if (!normalizedQuestion || submitting) return;
    requestSequenceRef.current += 1;
    const requestId = `${requestSequenceRef.current}`;
    setQuestion("");
    setSubmitting(true);
    setMessages((current) => [
      ...current,
      { id: `user-${requestId}`, role: "user", question: normalizedQuestion },
      { id: `assistant-${requestId}`, role: "assistant", pending: true },
    ]);

    try {
      const response = await askManufacturingAssistant({ question: normalizedQuestion, context });
      setMessages((current) => current.map((message) => message.id === `assistant-${requestId}` ? { ...message, pending: false, response } : message));
    } catch (error) {
      const detail = error instanceof Error ? error.message : "AI 助手暂时无法响应，请稍后重试。";
      setMessages((current) => current.map((message) => message.id === `assistant-${requestId}` ? { ...message, pending: false, error: detail } : message));
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) return null;

  return (
    <GsDrawerHost size={460} onClose={() => { if (!submitting) onClose(); }}>
      <aside className="aiAssistantPanel" role="dialog" aria-modal="true" aria-labelledby="ai-assistant-title">
        <header className="aiAssistantHeader">
          <span className="aiAssistantMark"><MaterialIcon name="auto_awesome" filled size={21} /></span>
          <div><span><strong id="ai-assistant-title">AI 制造助手</strong><em>前端演示</em></span><small>{context.workspace} · {context.pageTitle}</small></div>
          {messages.length ? <GsButton intent="text" onClick={() => setMessages([])} disabled={submitting} title="清空会话" aria-label="清空会话"><MaterialIcon name="delete_sweep" size={19} /></GsButton> : null}
          <GsButton intent="text" onClick={onClose} disabled={submitting} aria-label="关闭 AI 助手"><MaterialIcon name="close" size={21} /></GsButton>
        </header>

        <div className="aiAssistantFeed" ref={feedRef} aria-live="polite">
          <section className="aiWelcome">
            <span><MaterialIcon name="manufacturing" size={22} /></span>
            <div><strong>你好，{userName}</strong><p>我可以结合当前页面，辅助分析经营风险、订单履约、计划产能与生产异常。</p></div>
          </section>

          {!messages.length ? (
            <section className="aiStarter">
              <small>基于当前页面开始</small>
              <div>{quickPrompts.map((prompt) => <GsButton key={prompt} onClick={() => void submitQuestion(prompt)}><span>{prompt}</span><MaterialIcon name="arrow_outward" size={17} /></GsButton>)}</div>
            </section>
          ) : null}

          {messages.map((message) => message.role === "user" ? (
            <div className="aiMessage aiMessageUser" key={message.id}><p>{message.question}</p></div>
          ) : (
            <div className="aiMessage aiMessageAssistant" key={message.id}>
              <span className="aiMessageIcon"><MaterialIcon name="auto_awesome" filled size={17} /></span>
              {message.pending ? <div className="aiThinking"><i /><i /><i /><span>正在分析当前业务上下文</span></div> : null}
              {message.error ? <div className="aiError"><strong>暂时无法完成分析</strong><p>{message.error}</p></div> : null}
              {message.response ? (
                <article className="aiAnswer">
                  <div className="aiAnswerMeta"><span>建议置信度 · {message.response.confidence}</span><em>模拟数据</em></div>
                  <p>{message.response.summary}</p>
                  <ul>{message.response.insights.map((insight) => <li key={insight}>{insight}</li>)}</ul>
                  <section className="aiSources"><strong><MaterialIcon name="fact_check" size={16} />参考来源</strong>{message.response.sources.map((source) => <Link href={source.href} key={`${source.href}-${source.label}`} onClick={onClose}><span>{source.label}<small>{source.detail}</small></span><MaterialIcon name="chevron_right" size={17} /></Link>)}</section>
                  <div className="aiActions">{message.response.actions.map((action) => <Link href={action.href} key={`${action.href}-${action.label}`} onClick={onClose}>{action.label}<MaterialIcon name="arrow_forward" size={16} /></Link>)}</div>
                </article>
              ) : null}
            </div>
          ))}
        </div>

        <footer className="aiAssistantComposer">
          <div className="aiComposer">
            <GsTextArea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void submitQuestion();
                }
              }}
              rows={2}
              placeholder={`询问${context.pageTitle}相关问题`}
              disabled={submitting}
            />
            <GsButton intent="primary" onClick={() => void submitQuestion()} disabled={submitting || !question.trim()} aria-label="发送问题"><MaterialIcon name="arrow_upward" size={19} /></GsButton>
          </div>
          <p><MaterialIcon name="verified_user" size={14} />AI 仅提供辅助建议，不会自动修改业务数据；关键动作需人工确认。</p>
        </footer>
      </aside>
    </GsDrawerHost>
  );
}
