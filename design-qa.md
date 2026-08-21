# 左侧导航设计 QA

- source visual truth path: `C:/Users/Administrator/.codex/generated_images/019ffbad-7b73-7852-ada0-dd7803b4c1fd/exec-c60b4891-bb18-4766-9d3b-c6fd6718def0.png`
- normalized reference copy: `F:/GuanSeq/output/design-qa/navigation-reference.png`
- implementation screenshot path: `F:/GuanSeq/output/design-qa/navigation-implementation-1440.png`
- viewport: 1440 × 1024 CSS px
- source pixels: 1536 × 1024; implementation pixels: 1440 × 1024
- density normalization: 1× CSS capture; comparison focuses on the left navigation region because the selected reference intentionally preserves the existing dashboard.
- state: 经营工作台展开，经营总览选中

## Full-view comparison evidence

实现与参考保持相同的深蓝连续侧栏、白色品牌区、红色一级当前标记、浅蓝二级当前行以及紧凑的一二级树形结构。顶栏和主工作区未因侧栏重构发生信息架构变化。

## Focused region comparison evidence

重点检查左侧 0–252px 区域：

- 字体与层级：一级 13px、中等字重；二级 12px；当前项加粗。
- 布局节奏：一级行高 48px，二级行高 39px，侧栏宽度 252px。
- 色彩：`#071d34` 主背景，`#163553` 当前二级行，`#ef5147` 当前状态微强调。
- 图标：继续使用项目既有 Material Symbols Rounded，与参考方向一致。
- 文案：保留原有全部栏目名与 URL，不显示 ERP/MES 等缩写徽标。

## Comparison history

1. 初始实现存在 P1：浅色侧栏、深色卡片、模块缩写徽标与选中层级冲突。
2. 修复：改为连续深蓝底；删除“业务导航 / 12 个模块”和缩写徽标；统一一级红条与二级浅蓝行两种状态。
3. Post-fix evidence: `output/design-qa/navigation-implementation-1440.png`。未发现剩余 P0/P1/P2 差异。

## Findings

- P3：参考图侧栏约 280px，实现为 252px；这是有意保留更多业务工作区空间的轻微比例差异，不影响层级与可读性。
- P3：参考图生成字体与本地自托管中文字体字形略有差异，现有字体更适合真实产品渲染。

## Interaction and console verification

- 一级栏目展开/收起：通过现有路由状态与点击逻辑保留。
- 二、三级 URL 导航：保留原有 Link 路由。
- 移动端导航抽屉：沿用现有响应式行为。
- Browser console: 无 error / warning。
- `pnpm lint`: passed。
- `pnpm typecheck`: passed。

final result: passed

---

# 顶部导航设计 QA

- source visual truth path: `C:/Users/Administrator/.codex/generated_images/019ffbad-7b73-7852-ada0-dd7803b4c1fd/exec-4f0b2ebb-c39e-48f6-8084-23c8b0bb1c14.png`
- normalized reference copy: `F:/GuanSeq/output/design-qa/topbar-reference.png`
- implementation screenshot path: `F:/GuanSeq/output/design-qa/topbar-implementation-1440.png`
- viewport: 1440 × 1024 CSS px
- source pixels: 1488 × 1058; implementation pixels: 1440 × 1024
- density normalization: 1× CSS capture；按同一桌面状态对比顶部 70px 区域。
- state: 经营工作台 / 经营总览，搜索关闭，通知 3 条，用户林浩。

## Full-view comparison evidence

实现保留参考稿的白色极简顶栏、左侧细竖线页面身份、浅灰搜索框、无边框通知以及头像/姓名/职位用户区。根据产品决定移除了组织切换入口，其余主页面与选中深色侧栏保持不变。

## Focused region comparison evidence

- 字体与层级：页面标题 15px，路径 9px；用户姓名 11px、职位 8px。
- 布局节奏：顶栏 70px，左侧身份区与右侧工具区两端对齐；搜索框 276px。
- 色彩：白色工作面、午夜蓝身份线与头像、浅钛灰搜索底、信号红通知点。
- 图标：沿用 Material Symbols Rounded。
- 文案：移除“当前组织”；保留页面路径、搜索、通知、林浩与计划主管。

## Comparison history

1. 初始实现存在 P1：组织、搜索、通知和用户区各自套框，像多个独立组件拼接。
2. 修复：删除组织入口；页面身份改为竖线加双层文字；仅搜索保留浅色工作面；通知和用户区去除多余方框。
3. 第一轮实现存在 P2：旧 CSS 让用户下拉箭头继承头像方块样式。
4. 修复：头像样式限制到第一个子元素；Post-fix evidence: `output/design-qa/topbar-implementation-1440.png`。
5. 用户截图发现 P1：旧 `max-width: 1280px` 规则导致顶部搜索文字隐藏或折行；搜索弹层沿用旧版大尺寸，信息密度失衡。
6. 修复：顶部搜索强制单行并在 1280px 保留 230px 宽度；弹层收紧为 620px 宽、56px 输入行和紧凑提示区。Post-fix evidence: `output/design-qa/topbar-search-closed-1280.png`、`output/design-qa/topbar-search-open-1280.png`。

## Findings

- P3：参考稿包含组织切换，本实现按产品决定有意移除，右侧空间更安静。
- P3：参考稿头像为 38px 左右，本实现为 34px，更符合当前 70px 顶栏密度。

## Interaction and console verification

- 全局搜索：按钮与 Ctrl/Cmd+K 快捷键逻辑保留。
- 通知与用户入口：交互入口保留。
- 响应式：窄屏搜索收缩，手机端隐藏用户补充文字。
- 1280px 回归：搜索文本单行显示；搜索弹层无过大空白。
- Browser console: 无 error / warning。
- `pnpm lint`: passed。
- `pnpm typecheck`: passed。

final result: passed
