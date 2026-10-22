# android-template/CLAUDE.md

Android アプリの雛形（Jetpack Compose + MVVM。Hilt / Room / Retrofit / Moshi / Coil 入り）。元は外部のテンプレート（`README.md` 参照）。

## ルール
- **このフォルダは直接改造しない**。新しい Android アプリは、新しいフォルダを作ってここから必要なファイルをコピーして始める。
  手順は `../build.md` の「新しいアプリを始める手順」、実例は `../amazon-ai-reader-app/`。
- 雛形のバージョン（AGP / Kotlin / Compose BOM など）を上げるときは、`gradle/libs.versions.toml` を更新する。
  各アプリは雛形をコピーした時点のバージョンなので、必要ならアプリ側も個別に上げる。

## ビルドとテスト
- `.github/workflows/merge-build-and-test-workflow.yml`（master への push）と
  `.github/workflows/pr-build-and-test-workflow.yml`（master への PR）が、このフォルダの変更時に `./gradlew test` を実行する。
- サンドボックスではビルドできない（`../build.md`）。
