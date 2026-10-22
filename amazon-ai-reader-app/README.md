# Amazon AI Reader（Androidアプリ）

AmazonのURLを入れる（またはAmazonアプリの「共有」からこのアプリを選ぶ）と、商品ページ・検索結果を
AIに渡しやすい Markdown / JSON に変換するアプリです。サーバーは不要です。

## 仕組み
- アプリ内の WebView（見えないブラウザ）がAmazonのページを普通に開く
- `app/src/main/assets/extractor.js` を流し込んで変換する
  （`amazon-to-ai/index.html` の変換ロジックのコピー。片方を直したらもう片方にも反映すること）
- Amazonに確認画面（CAPTCHA）が出たら、ページを表示して手で解いてから「このページを変換」を押す

## インストール
`amazon-ai-reader-app/` 以下を変更してプッシュすると、GitHub Actions（`.github/workflows/amazon-ai-reader-app.yml`）が
APKをビルドし、リリース「amazon-ai-reader-latest」に `amazon-ai-reader.apk` を置きます。
スマホでリリースページを開いてダウンロードし、インストールしてください（初回は「提供元不明のアプリ」の許可が必要）。

`app/signing.keystore` は個人インストール用の固定署名鍵です。毎回同じ鍵で署名されるので上書き更新できます。
Google Play での配布には使わないでください。

## 手元でビルドする場合
Android SDK と JDK 21 を用意して、このフォルダで `./gradlew assembleDebug`。
