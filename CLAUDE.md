# CLAUDE.md

このリポジトリで作業するときの案内です。作業を始める前に、関係する知見ファイルを読んでください。

## リポジトリの構成
| パス | 内容 |
|---|---|
| `app/` ほかルート直下の Gradle 設定 | Android アプリの**雛形**（Jetpack Compose + MVVM テンプレート）。直接は改造しない |
| `amazon-to-ai/` | Amazon のページを AI 向け Markdown/JSON に変換する Web アプリ（`index.html`）と、URL 取得用の Node サーバー（`server.js`） |
| `amazon-ai-reader-app/` | 上記の Android アプリ版（WebView でページを開いて変換。共有メニュー対応） |
| `.github/workflows/amazon-ai-reader-app.yml` | Android アプリ版のビルド → エミュレーター確認 → APK をリリースに配置 |

## 知見ファイルの目次
| ファイル | 内容 | 読むタイミング |
|---|---|---|
| [build.md](build.md) | 雛形のバージョン、Gradle 設定、固定署名鍵、GitHub Actions、APK をリリースで配る方法、CI 結果の取り方 | Android アプリのビルド設定やワークフローを触るとき。サンドボックスで APK が作れないと気づいたとき |
| [testing.md](testing.md) | CI のエミュレーターで画面・ログを取る方法と注意点、Playwright での Web 確認、ダミー応答でのサーバー確認 | 動作確認をするとき。アプリが起動しない・落ちる原因を調べるとき |
| [android.md](android.md) | Compose と WebView の落とし穴（白い画面）、WebView からの読み取り、共有メニュー対応、インストール時のトラブル | Android アプリの画面や機能を実装するとき |
| [amazon.md](amazon.md) | Amazon ページの取得方法の比較、URL の正規化と許可ドメイン、CAPTCHA の見分け方、HTML 構造、評価表記などの落とし穴 | 変換ロジック（extractor）や取得処理を直すとき |
| [environment.md](environment.md) | サンドボックスで繋がる/繋がらない先、Artifact の制限、ユーザーの端末環境（Termux + Ubuntu） | 環境が原因っぽいエラーのとき。ユーザーに操作手順を案内するとき |

新しい知見が得られたら、該当するファイルに追記してください。どれにも当てはまらない場合は新しいカテゴリのファイルを作り、この目次に追加します。

## ルール

### Android アプリは「雛形をコピーした新しいフォルダ」で作る
新しい Android アプリを開発するときは、ルートの雛形を直接改造せず、**新しいフォルダを作って雛形の必要なファイルをコピーしてから始める**。
`amazon-ai-reader-app/` がこの手順で作った実例。

1. `<アプリ名>/` フォルダを作る
2. 雛形からコピーする：
   - `gradlew` / `gradlew.bat` / `gradle/wrapper/*`
   - `gradle.properties`
   - `gradle/libs.versions.toml`（バージョンはそのまま。使うライブラリだけ残す）
   - `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts` の書き方（`rootProject.name`・`namespace`・`applicationId` を変える）
   - `app/src/main/res/xml/*`（バックアップ設定）
3. フォルダ専用の `.gitignore` を置く
4. スマホにインストールするなら固定の署名鍵を作る（[build.md](build.md)）
5. `.github/workflows/<アプリ名>.yml` を作り、`paths: ["<アプリ名>/**"]` でそのフォルダの変更時だけビルドする。
   エミュレーター確認のジョブも付ける（[testing.md](testing.md)）
6. サンドボックスでは APK をビルドできないので、プッシュして GitHub Actions の結果で確認する

### 変換ロジックは2か所にある
`amazon-to-ai/index.html` の `<script id="extractor">` と `amazon-ai-reader-app/app/src/main/assets/extractor.js` は同じコード。
片方を直したら、もう片方にも同じ修正を入れる。

### その他
- 作業ブランチにだけあるワークフローは手動実行（workflow_dispatch）できない。`push` トリガーで動かす。
- ユーザーはスマホから操作していることが多い。手順は短く、スマホで実行できる形で案内する。
