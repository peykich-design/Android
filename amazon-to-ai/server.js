// Amazon AI Reader のサーバー版。
// index.html を配信し、/api/fetch?url=... でAmazonのページHTMLを代わりに取得する。
// 依存ライブラリなし（Node.js 18 以上）。起動: node server.js  → http://localhost:3000
"use strict";
const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = Number(process.env.PORT) || 3000;
const MAX_BYTES = 8 * 1024 * 1024;
const TIMEOUT_MS = 15000;

// 任意サイトの中継に使われないよう、Amazon と公式短縮URLのドメインだけを許可する
const ALLOWED_HOST = /^(?:[a-z0-9-]+\.)*amazon\.(?:co\.jp|com|co\.uk|de|fr|it|es|ca|com\.au|com\.mx|com\.br|in|nl|se|pl|sg|ae|sa|com\.tr|com\.be|eg)$|^(?:amzn\.asia|amzn\.to|amzn\.eu|a\.co)$/i;

const HEADERS = {
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "ja-JP,ja;q=0.9,en;q=0.8",
};

function checkUrl(raw) {
  let u;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== "https:" && u.protocol !== "http:") return null;
  if (!ALLOWED_HOST.test(u.hostname)) return null;
  u.protocol = "https:";
  // 商品ページは余計なパラメータを落として /dp/ASIN に正規化する
  const m = u.pathname.match(/\/(?:dp|gp\/product|gp\/aw\/d)\/([A-Z0-9]{10})/i);
  if (m) return new URL(`https://${u.hostname}/dp/${m[1].toUpperCase()}`);
  return u;
}

async function fetchAmazon(startUrl) {
  let url = startUrl;
  for (let hop = 0; hop < 5; hop++) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
    let res;
    try {
      res = await fetch(url, { headers: HEADERS, redirect: "manual", signal: ctrl.signal });
    } finally { clearTimeout(timer); }

    if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
      // リダイレクト先も許可ドメインか確認してから追う（短縮URL対応）
      const next = checkUrl(new URL(res.headers.get("location"), url).href);
      if (!next) throw httpError(400, "Amazon以外のサイトへ転送されたため中止しました。");
      url = next;
      continue;
    }

    const html = await readLimited(res);
    if (/captcha|api-services-support@amazon\.com|To discuss automated access/i.test(html) && !/id="productTitle"|s-search-result/.test(html)) {
      throw httpError(502, "Amazonに自動アクセスと判定されブロックされました（CAPTCHA）。時間をおいて再試行するか、HTML貼り付け・ブックマークレットを使ってください。");
    }
    if (!res.ok) throw httpError(502, `Amazonからエラーが返りました（HTTP ${res.status}）。URLを確認するか、時間をおいて再試行してください。`);
    return { url: url.href, html };
  }
  throw httpError(502, "リダイレクトが多すぎます。");
}

async function readLimited(res) {
  const chunks = [];
  let size = 0;
  for await (const chunk of res.body) {
    size += chunk.length;
    if (size > MAX_BYTES) throw httpError(502, "ページが大きすぎます。");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

function httpError(status, message) { return Object.assign(new Error(message), { status }); }

function sendJson(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
  res.end(JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  const reqUrl = new URL(req.url, "http://localhost");

  if (req.method === "GET" && reqUrl.pathname === "/api/fetch") {
    const target = checkUrl(reqUrl.searchParams.get("url") || "");
    if (!target) return sendJson(res, 400, { error: "AmazonのURL（amazon.co.jp / amazon.com / amzn.asia など）を入力してください。" });
    try {
      sendJson(res, 200, await fetchAmazon(target));
    } catch (e) {
      const msg = e.status ? e.message : e.name === "AbortError" ? "Amazonの応答がタイムアウトしました。" : "Amazonに接続できませんでした：" + e.message;
      sendJson(res, e.status || 502, { error: msg });
    }
    return;
  }

  if (req.method === "GET" && (reqUrl.pathname === "/" || reqUrl.pathname === "/index.html")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    fs.createReadStream(path.join(__dirname, "index.html")).pipe(res);
    return;
  }

  res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
  res.end("Not found");
});

if (require.main === module) {
  server.listen(PORT, () => console.log(`Amazon AI Reader: http://localhost:${PORT}`));
}
module.exports = { server, checkUrl };
