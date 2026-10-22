# amazon-to-ai/CLAUDE.md

Amazon のページを AI 向け Markdown/JSON に変換する Web アプリ。このフォルダでの作業に関する決まりです。

## 構成
- `index.html`：画面と変換ロジック（1ファイルで完結。ブラウザだけでも動く）
- `server.js`：`index.html` の配信と、URL から Amazon の HTML を取得する `/api/fetch`（依存なし、Node.js 18 以上）
- `package.json`：`npm start` で `server.js` を起動

## ルール
- **変換ロジックは2か所にある**：`index.html` の `<script id="extractor">` と
  `../amazon-ai-reader-app/app/src/main/assets/extractor.js` は同じコード。片方を直したら、もう片方にも同じ修正を入れる。
- `<script id="extractor">` の中身はブックマークレットにもそのまま埋め込まれる。DOM 以外（画面の部品や外部ライブラリ）に依存させない。
- `server.js` は Amazon と公式短縮 URL 以外を取得しない（リダイレクト先も確認）。許可ドメインを変えるときは
  アプリ版 `AmazonLoader.kt` の `ALLOWED_HOST` もそろえる。

## 公開ページ（Artifact）の更新
- 公開先：https://claude.ai/artifact/QunN14BWtcZKvMTfVxBUct
- `index.html` の先頭3行（doctype・html・meta）を落としたものを公開する：
  `tail -n +4 index.html > <スクラッチパッド>/amazon-ai-reader.html` → そのファイルを Artifact で公開。
- Artifact 上では外部通信ができないので「URLから変換」は動かない（案内メッセージが出るのが正常）。

## 動作確認
- Playwright で `file://.../index.html` を開いて確認する。サーバーはダミーの `fetch` で確認する（手順は `../testing.md`）。
