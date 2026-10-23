# soraraw-app/CLAUDE.md

https://soraraw.com/ を表示するだけの、自分用の Android WebView アプリ。Compose は使わず、View（XML）+ WebView で作っている。
このフォルダでの作業に関する決まりです。

## 構成
- `app/src/main/java/com/peykich/soraraw/`
  - `MainActivity.kt`：WebView の設定、エッジツーエッジ、戻るボタン、リンクの振り分け、ファイル選択、エラー画面
  - `Downloads.kt`：ダウンロード（http/https は DownloadManager、data:/blob: は自前で「ダウンロード」に保存）
  - `SiteRules.kt`：表示する URL と「アプリ内で開くドメイン」の判定（単体テストあり）
- `app/src/main/res/values/app_name.xml`・`mipmap-anydpi-v26/ic_launcher.xml`：**仮の**アプリ名とアイコン
- `tools/fetch_site_meta.py`：CI のビルド前に、サイトの manifest → favicon/title からアプリ名とアイコンを取得してリソースを上書きする

## ルール
- 対象サイトを変えるときは `SiteRules.kt`（`START_URL`・`HOST`）と、ワークフローの `fetch_site_meta.py` の引数をそろえる。
- 回転で再読み込みしないよう、マニフェストの `configChanges` で Activity を作り直さない。
  ダークモード切り替え（`uiMode`）だけはあえて含めず、作り直してテーマを反映する（`saveState`/`restoreState` で履歴は戻る）。
- WebView の `prefers-color-scheme` は、アプリのテーマが `values-night` でダーク（`Theme.Material.NoActionBar`）になることで dark になる。
- 署名鍵はリポジトリに入れない（`.gitignore` で `*.jks` `*.keystore` を除外）。CI は Secrets から復元する：
  `SIGNING_KEYSTORE_BASE64`（キーストアを base64 にしたもの）/ `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS`（/ 任意で `SIGNING_KEY_PASSWORD`）。
  未登録だとデバッグ鍵で署名され、上書きインストールできない。

## ビルドと確認
- サンドボックスではビルドできない（`../build.md`）。プッシュすると `.github/workflows/soraraw-app.yml` がビルドと単体テストを行い、
  APK を Artifact `soraraw-apk` に置く。master ではリリース `soraraw-latest` にも置く。
- 最新 APK（master）：https://github.com/peykich-design/Application/releases/download/soraraw-latest/soraraw.apk
- サイト情報の取得結果は、Actions の実行結果ページの Summary（「サイト情報の取得」）に出る。
- 2026年10月の作成時点では、soraraw.com はサンドボックス・WebFetch・GitHub Actions のどれからも名前解決できず、仮の名前とアイコンになっている。
