# amazon-ai-reader-app/CLAUDE.md

Amazon AI Reader の Android アプリ版。WebView で Amazon のページを開き、変換ロジックを流し込んで Markdown/JSON にする。
このフォルダでの作業に関する決まりです。

## 構成
- `app/src/main/assets/extractor.js`：変換ロジック（Web 版のコピー）
- `app/src/main/java/com/peykich/amazonaireader/`
  - `AmazonLoader.kt`：WebView での読み込み、CAPTCHA 検知、変換の実行、URL の許可・正規化
  - `ReaderScreen.kt`：画面（Compose）
  - `MainActivity.kt`：起動と共有メニュー（`ACTION_SEND`）の受け取り
- `app/signing.keystore`：上書きインストール用の固定署名鍵（Google Play 用ではない）
- `tools/device-check.sh`：CI のエミュレーターで画面とログを集めるスクリプト

## ルール
- **変換ロジックは2か所にある**：`app/src/main/assets/extractor.js` と `../amazon-to-ai/index.html` の
  `<script id="extractor">` は同じコード。片方を直したら、もう片方にも同じ修正を入れる。
- **WebView はスクロール領域（`verticalScroll`）の中に置かない**。白い背景が画面全体を覆って真っ白になる（`../android.md`）。
- 許可ドメイン（`AmazonLoader.kt` の `ALLOWED_HOST`）を変えるときは `../amazon-to-ai/server.js` もそろえる。

## ビルドと確認
- サンドボックスではビルドできない。プッシュすると `.github/workflows/amazon-ai-reader-app.yml` が
  ビルド → エミュレーター確認 → リリース配置まで行う（約15分）。
- 最新 APK：https://github.com/peykich-design/Android/releases/download/amazon-ai-reader-latest/amazon-ai-reader.apk
- 確認結果（スクリーンショット・画面構造・logcat）：リリース `amazon-ai-reader-check` の
  `1-launch.png` / `2-share.png` / `*.xml` / `logcat.txt`。
- エミュレーターでは Amazon が確認画面（CAPTCHA）になるため、実際の変換結果はユーザーの端末で確認してもらう。
