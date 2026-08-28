import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = process.cwd();
const read = (path) => readFileSync(resolve(root, path), "utf8");
const fail = (message) => {
  console.error(`[FAIL] ${message}`);
  process.exitCode = 1;
};

const packageJson = JSON.parse(read("package.json"));
const version = packageJson.version;
if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) {
  fail(`package.json 版本不是明确的语义化版本：${version}`);
}

const pom = read("guanseq-server/pom.xml");
const pomVersion = pom.match(/<artifactId>guanseq-server<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1];
if (pomVersion !== version) {
  fail(`前后端版本不一致：package.json=${version}，pom.xml=${pomVersion ?? "未找到"}`);
}

const expectedValues = [
  ["Dockerfile", `ARG GUANSEQ_BUILD_VERSION=${version}`],
  [".env.production.example", `GUANSEQ_VERSION=${version}`],
  ["compose.production.yaml", `\${GUANSEQ_VERSION:-${version}}`],
  ["src/app/api/health/route.ts", `\"${version}\"`],
  ["guanseq-server/src/main/resources/application.yml", `\${GUANSEQ_BUILD_VERSION:${version}}`],
  ["guanseq-server/src/main/resources/openapi/guanseq-api-v1.yaml", `version: ${version}`],
  ["CHANGELOG.md", `## [${version}]`],
];

for (const [path, expected] of expectedValues) {
  if (!read(path).includes(expected)) {
    fail(`${path} 未声明当前版本 ${version}`);
  }
}

const releaseNotes = `docs/releases/${version}.md`;
if (!existsSync(resolve(root, releaseNotes))) {
  fail(`缺少发行说明 ${releaseNotes}`);
}

const tagIndex = process.argv.indexOf("--tag");
if (tagIndex >= 0) {
  const tag = process.argv[tagIndex + 1];
  if (!tag) {
    fail("--tag 后必须提供标签");
  } else if (tag !== `v${version}`) {
    fail(`标签与源码版本不一致：tag=${tag}，expected=v${version}`);
  }
}

if (!process.exitCode) {
  console.log(`[PASS] GuanSeq ${version} 版本声明和发行说明一致`);
}
