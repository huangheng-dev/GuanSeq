export type UserProfile = {
  name: string;
  title: string;
  department: string;
  email: string;
  phone: string;
  locale: string;
  notificationPreference: string;
};

export type CapabilityFeedback = {
  id: string;
  pathname: string;
  type: string;
  priority: string;
  scenario: string;
  expectation: string;
  submittedAt: string;
};

export type RecordComment = {
  id: string;
  author: string;
  content: string;
  createdAt: string;
};

export type RecordAttachment = {
  id: string;
  name: string;
  size: number;
  createdAt: string;
};

export type RecordCollaboration = {
  comments: RecordComment[];
  attachments: RecordAttachment[];
};

export type SavedBusinessView = {
  id: string;
  name: string;
  query: string;
  status: string;
  owner: string;
  period: string;
  sortColumn: number;
  sortDirection: "asc" | "desc";
  visibleColumnIndexes: number[];
};

export const defaultUserProfile: UserProfile = {
  name: "林浩",
  title: "计划主管",
  department: "计划管理部",
  email: "lin.hao@guanseq.example",
  phone: "138 0000 2608",
  locale: "简体中文",
  notificationPreference: "重要业务与风险",
};

const profileStorageKey = "guanseq-user-profile";
const feedbackStoragePrefix = "guanseq-capability-feedback:";
const collaborationStoragePrefix = "guanseq-record-collaboration:";
const savedViewStoragePrefix = "guanseq-saved-views:";

function delay(duration = 420) {
  return new Promise((resolve) => setTimeout(resolve, duration));
}

function safeRead<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    return JSON.parse(window.localStorage.getItem(key) ?? "null") as T ?? fallback;
  } catch {
    return fallback;
  }
}

function safeWrite(key: string, value: unknown) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 本地存储不可用时仍保留当前会话交互。
  }
}

export function readUserProfile(): UserProfile {
  const stored = safeRead<Partial<UserProfile> | null>(profileStorageKey, null);
  return stored ? { ...defaultUserProfile, ...stored } : defaultUserProfile;
}

export async function saveUserProfile(profile: UserProfile): Promise<UserProfile> {
  if (!profile.name.trim() || !profile.title.trim() || !profile.email.trim()) throw new Error("请填写姓名、职位和邮箱。");
  await delay();
  const nextProfile = Object.fromEntries(Object.entries(profile).map(([key, value]) => [key, value.trim()])) as UserProfile;
  safeWrite(profileStorageKey, nextProfile);
  return nextProfile;
}

export function readCapabilityFeedbacks(pathname: string): CapabilityFeedback[] {
  const feedbacks = safeRead<CapabilityFeedback[]>(`${feedbackStoragePrefix}${pathname}`, []);
  return Array.isArray(feedbacks) ? feedbacks : [];
}

export async function submitCapabilityFeedback(input: Omit<CapabilityFeedback, "id" | "submittedAt">): Promise<CapabilityFeedback> {
  if (!input.scenario.trim() || !input.expectation.trim()) throw new Error("请补充使用场景和期望能力。");
  await delay(560);
  const feedback: CapabilityFeedback = {
    ...input,
    id: `FB-${Math.random().toString(36).slice(2, 8).toUpperCase()}`,
    submittedAt: new Date().toISOString(),
  };
  safeWrite(`${feedbackStoragePrefix}${input.pathname}`, [feedback, ...readCapabilityFeedbacks(input.pathname)].slice(0, 20));
  return feedback;
}

export function readRecordCollaboration(pathname: string, rowId: string): RecordCollaboration {
  const stored = safeRead<Partial<RecordCollaboration> | null>(`${collaborationStoragePrefix}${pathname}:${rowId}`, null);
  return {
    comments: Array.isArray(stored?.comments) ? stored.comments : [],
    attachments: Array.isArray(stored?.attachments) ? stored.attachments : [],
  };
}

export async function addRecordComment(pathname: string, rowId: string, author: string, content: string): Promise<RecordCollaboration> {
  if (!content.trim()) throw new Error("请输入协作内容。");
  await delay(320);
  const current = readRecordCollaboration(pathname, rowId);
  const next = {
    ...current,
    comments: [{ id: `C-${Date.now()}`, author, content: content.trim(), createdAt: new Date().toISOString() }, ...current.comments],
  };
  safeWrite(`${collaborationStoragePrefix}${pathname}:${rowId}`, next);
  return next;
}

export async function addRecordAttachment(pathname: string, rowId: string, file: File): Promise<RecordCollaboration> {
  await delay(360);
  const current = readRecordCollaboration(pathname, rowId);
  const next = {
    ...current,
    attachments: [{ id: `A-${Date.now()}`, name: file.name, size: file.size, createdAt: new Date().toISOString() }, ...current.attachments],
  };
  safeWrite(`${collaborationStoragePrefix}${pathname}:${rowId}`, next);
  return next;
}

export function readSavedBusinessViews(pathname: string): SavedBusinessView[] {
  const views = safeRead<SavedBusinessView[]>(`${savedViewStoragePrefix}${pathname}`, []);
  return Array.isArray(views) ? views : [];
}

export async function saveBusinessView(pathname: string, input: Omit<SavedBusinessView, "id">): Promise<SavedBusinessView[]> {
  if (!input.name.trim()) throw new Error("请输入视图名称。");
  await delay(260);
  const current = readSavedBusinessViews(pathname);
  const nextView = { ...input, id: `VIEW-${Date.now()}`, name: input.name.trim() };
  const next = [nextView, ...current.filter((view) => view.name !== nextView.name)].slice(0, 8);
  safeWrite(`${savedViewStoragePrefix}${pathname}`, next);
  return next;
}

export function deleteBusinessView(pathname: string, id: string): SavedBusinessView[] {
  const next = readSavedBusinessViews(pathname).filter((view) => view.id !== id);
  safeWrite(`${savedViewStoragePrefix}${pathname}`, next);
  return next;
}
