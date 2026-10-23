import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

// リリース署名の情報は環境変数から読む（GitHub Actions では Secrets から渡す）。
// キーストア本体はリポジトリに入れない。
// Secrets が未登録だと空文字で渡ってくるので、空の値は「なし」として扱う
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseKeystore = env("SIGNING_KEYSTORE_PATH")
val hasReleaseKey = releaseKeystore != null && file(releaseKeystore).exists()

android {
    namespace = "com.peykich.soraraw"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.peykich.soraraw"
        minSdk = 26
        targetSdk = 37
        // CI の実行番号を使い、新しいビルドほど大きい番号にする
        versionCode = env("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = env("SIGNING_STORE_PASSWORD")
                keyAlias = env("SIGNING_KEY_ALIAS")
                // 鍵のパスワードを別に登録していなければ、ストアのパスワードと同じとみなす
                keyPassword = env("SIGNING_KEY_PASSWORD") ?: env("SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 鍵がないとき（Secrets 登録前など）はデバッグ鍵で署名する。
            // この APK は鍵が毎回変わるので、上書きインストールには使えない。
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
}
