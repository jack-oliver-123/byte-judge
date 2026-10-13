/**
 * 从前端后端的 OpenAPI 文档生成 TypeScript 客户端。
 *
 * 用法：npm run gen:api
 * 前置：byte-judge-backend 已启动（文档端点见下方 OPENAPI_URL）。
 *
 * 产物写到 src/generated/，整个目录都是生成的，不要手改——
 * 后端接口一变就重新跑本脚本覆盖。手写的适配只留在 scripts/openapi-request.ts，
 * 由 --request 在生成时覆盖掉默认的 core/request.ts。
 */

const { spawnSync } = require("child_process");
const http = require("http");
const path = require("path");

// 后端 server.port 8101 + context-path /api，Knife4j(OpenAPI 3) 的默认文档端点
const OPENAPI_URL =
  process.env.OPENAPI_URL || "http://localhost:8101/api/v3/api-docs";

const outputDir = path.resolve(__dirname, "../src/generated");
const requestFile = path.resolve(__dirname, "openapi-request.ts");
const openapiBin = path.resolve(
  __dirname,
  "../node_modules/openapi-typescript-codegen/bin/index.js"
);

/**
 * 生成器在文档端点不可达时抛的是 json-schema-ref-parser 的解析异常，
 * 看不出是后端没启动。先探测一次，失败时给出可操作的提示再退出。
 */
function ensureSpecReachable(url) {
  return new Promise((resolve) => {
    const req = http.get(url, (res) => {
      res.resume();
      resolve(res.statusCode);
    });
    req.on("error", () => resolve(0));
    req.setTimeout(5000, () => {
      req.destroy();
      resolve(0);
    });
  });
}

ensureSpecReachable(OPENAPI_URL).then((status) => {
  if (status !== 200) {
    console.error(
      `无法获取 OpenAPI 文档：GET ${OPENAPI_URL}（${
        status === 0 ? "连接失败" : "HTTP " + status
      }）。\n请先启动 byte-judge-backend，或用 OPENAPI_URL 指向一份规范文件后重试。`
    );
    process.exit(1);
  }

  const result = spawnSync(
    process.execPath,
    [
      openapiBin,
      "--input",
      OPENAPI_URL,
      "--output",
      outputDir,
      "--client",
      "axios",
      "--request",
      requestFile,
      "--useUnionTypes",
      "--indent",
      "2",
    ],
    { stdio: "inherit" }
  );

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
});
