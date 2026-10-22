# CLAUDE.md

このリポジトリで作業するときの案内です。作業を始める前に、関係する知見ファイルを読んでください。

## リポジトリの構成
| パス | 内容 |
|---|---|
| `app/` ほかルート直下の Gradle 設定 | Android アプリの**雛形**（Jetpack Compose + MVVM テンプレート）。直接は改造しない |
| `amazon-to-ai/` | Amazon のページを AI 向け Markdown/JSON に変換する Web アプリと、URL 取得用の Node サーバー。決まりは `amazon-to-ai/CLAUDE.md` |
| `amazon-ai-reader-app/` | 上記の Android アプリ版（WebView でページを開いて変換。共有メニュー対応）。決まりは `amazon-ai-reader-app/CLAUDE.md` |
| `.github/workflows/amazon-ai-reader-app.yml` | Android アプリ版のビルド → エミュレーター確認 → APK をリリースに配置 |

## 知見ファイルの目次
| ファイル | 内容 | 読むタイミング |
|---|---|---|
| [build.md](build.md) | 雛形のバージョン、Gradle 設定、固定署名鍵、GitHub Actions、APK をリリースで配る方法、CI 結果の取り方 | Android アプリのビルド設定やワークフローを触るとき。サンドボックスで APK が作れないと気づいたとき |
| [testing.md](testing.md) | CI のエミュレーターで画面・ログを取る方法と注意点、Playwright での Web 確認、ダミー応答でのサーバー確認 | 動作確認をするとき。アプリが起動しない・落ちる原因を調べるとき |
| [android.md](android.md) | Compose と WebView の落とし穴（白い画面）、WebView からの読み取り、共有メニュー対応、インストール時のトラブル | Android アプリの画面や機能を実装するとき |
| [amazon.md](amazon.md) | Amazon ページの取得方法の比較、URL の正規化と許可ドメイン、CAPTCHA の見分け方、HTML 構造、評価表記などの落とし穴 | 変換ロジック（extractor）や取得処理を直すとき |
| [ai-api.md](ai-api.md) | Claude（Java SDK）・Gemini（REST）の呼び出し方、Android での注意点、プロンプトの書き方 | AI の API を使う機能を作る・直すとき |
| [environment.md](environment.md) | サンドボックスで繋がる/繋がらない先、Artifact の制限、ユーザーの端末環境（Termux + Ubuntu） | 環境が原因っぽいエラーのとき。ユーザーに操作手順を案内するとき |

新しい知見が得られたら、該当するファイルに追記する。いつ・何を・どう書くかは [knowledge.md](knowledge.md) に従う。

## ルール

### フォルダ固有の内容は、そのフォルダの CLAUDE.md に書く
特定のフォルダの中でだけ有効な決まりや情報（構成、ファイル間で同期が必要な箇所、そのフォルダ専用のビルド・公開手順など）は、
ルートの CLAUDE.md ではなく**そのフォルダ直下の `CLAUDE.md`** に書く。フォルダに無ければ新しく作り、上の「リポジトリの構成」の表から参照する。
ルートの CLAUDE.md と知見ファイル（build.md など）には、リポジトリ全体や複数のフォルダに共通する内容だけを書く。

### Android アプリは「雛形をコピーした新しいフォルダ」で作る
新しい Android アプリは、ルートの雛形を直接改造せず、`<アプリ名>/` フォルダを作って雛形の必要なファイルをコピーしてから始める。
手順は [build.md](build.md) の「新しいアプリを始める手順」、実例は `amazon-ai-reader-app/`。

### その他
- 作業ブランチにだけあるワークフローは手動実行（workflow_dispatch）できない。`push` トリガーで動かす。
- ユーザーはスマホから操作していることが多い。手順は短く、スマホで実行できる形で案内する。
