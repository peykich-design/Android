# Amazon ページ読み取りの知見

対象：`amazon-to-ai/`（Web 版・Node サーバー）と `amazon-ai-reader-app/`（Android 版）。
変換ロジックは `amazon-to-ai/index.html` の `<script id="extractor">` と `amazon-ai-reader-app/app/src/main/assets/extractor.js`（コピー）。

## 取得方法の選び方
| 方法 | 長所 | 短所 |
|---|---|---|
| HTML を貼り付け | サーバー不要・確実 | スマホでは手間 |
| ブックマークレット | 普段のブラウザで実行、CAPTCHA なし | 登録が面倒、約 18KB と長い |
| Node サーバー（server.js） | URL だけで使える | 起動が必要。クラウドからだと CAPTCHA になりやすい |
| Android アプリ（WebView） | サーバー不要、共有メニューから使える | APK の配布・インストールが必要 |

- ブラウザ単体の Web ページ（PWA 含む）からは CORS のため Amazon を直接取得できない。claude.ai の Artifact も外部への通信は不可。
- データセンター（GitHub Actions など）の IP からは高確率で確認画面（CAPTCHA）になる。
  スマホの回線や、本人のブラウザ・端末内 WebView からの方が通りやすい。
- Amazon の利用規約は自動取得を制限している。個人で少量使う範囲にとどめる。

## URL の扱い
- 許可ドメイン：`amazon.(co.jp|com|co.uk|de|...)` と公式短縮 URL `amzn.asia` / `amzn.to` / `amzn.eu` / `a.co`。
  サーバーやアプリが任意サイトの中継に使われないよう、**リダイレクト先も毎回チェック**する。
- 商品ページは `/dp/ASIN`・`/gp/product/ASIN`・`/gp/aw/d/ASIN` から ASIN（10 桁英数字）を取り、`https://<host>/dp/<ASIN>` に正規化する。

## CAPTCHA（確認画面）の見分け方
- `form[action*="validateCaptcha"]` がある、または本文に `api-services-support@amazon.com` / `To discuss automated access` を含み、
  かつ `#productTitle` が無いとき。
- 「ショッピングを続ける」ボタンだけの画面もある。アプリでは画面を見せて、ユーザーが進めたら自動で変換を再開する。

## HTML 構造（2026 年時点で確認・想定したもの）
- 商品名 `#productTitle`、ブランド `#bylineInfo`、パンくず `#wayfinding-breadcrumbs_feature_div li a`
- 価格 `#corePriceDisplay_desktop_feature_div .a-price .a-offscreen`（参考価格は `.a-text-price`）
- 在庫 `#availability`、販売元 `#merchantInfoFeature_feature_div` など、評価 `#acrPopover`、評価数 `#acrCustomerReviewText`
- 特徴 `#feature-bullets li`、説明 `#productDescription`、仕様表 `#productDetails_*` / `#poExpander` / `#detailBullets_feature_div li`
- 画像 `#landingImage[data-old-hires]`、ASIN `input#ASIN`
- 検索結果 `div[data-component-type="s-search-result"][data-asin]`、広告は「スポンサー」を含む
- レビュー `[data-hook="review"]`（`#cm-cr-dp-review-list > li` の場合も）、
  タイトル `[data-hook="review-title"]`（a のことも span のこともある。中に星の `i` が入る）、
  本文 `[data-hook="review-body"] [data-hook="review-collapsed"]` など。見つからなければレビュー内で一番長い文章を本文とする。

## 落とし穴
- **評価の表記が2通り**：「5つ星のうち4.2」（数字が後ろ）と「4.2 out of 5 stars」。最初の数字を取ると「5」になる。
  `5\s*つ星のうち\s*([\d.]+)` → `([\d.]+)\s*out of` の順で読む。星アイコンのクラス `a-star-4-5` からも読める。
- **表のセルの中に `<script>` が埋め込まれている**（例：「おすすめ度」の行）。`textContent` だとコードが混ざるので、
  script/style/noscript を飛ばしてテキストを集める関数を使う。
- 「ASIN :」のような見出しはコロンの前の空白も消す。
- Amazon の HTML はよく変わる。取れない項目が出たら、実ページの該当部分の HTML をユーザーに出してもらって合わせる
  （Termux で `curl localhost:3000/api/fetch?url=...` の結果から該当箇所を抜き出すコマンドを渡すと早い）。
