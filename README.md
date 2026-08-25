# MATA

繰り返し設定に特化したAndroid向けTODO管理アプリです。

## 開発環境

- Android Studio
- JDK 17以上（Android Studio同梱JBRを利用可能）
- Android SDK Platform 37.1
- Gradle Wrapper 9.7.1
- Android Gradle Plugin 9.3.1
- Kotlin 2.2.10（AGP内蔵Kotlin）
- Jetpack Compose / Material 3

## Android設定

| 項目 | 値 |
| --- | --- |
| Namespace | `com.mochisofts.mata` |
| Release Application ID | `com.mochisofts.mata` |
| Debug Application ID | `com.mochisofts.mata.debug` |
| minSdk | 26（Android 8.0） |
| targetSdk | 36（Android 16） |
| compileSdk | 37.1 |

Debug版は端末上で「MATA Dev」と表示され、Release版と同時にインストールできます。

## 初回セットアップ

1. Android Studioでリポジトリを開く。
2. Gradle JDKにJDK 17以上を指定する。
3. SDK ManagerでAndroid SDK Platform 37.1をインストールする。
4. Gradle Syncを実行する。

`local.properties` はAndroid Studioが生成するローカル専用ファイルで、Gitには追加しません。

## ローカル検証

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS / Linux:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

生成されたDebug APKは `app/build/outputs/apk/debug/app-debug.apk` に出力されます。

## ブランチ運用

- `main`: 常にビルド可能な基準ブランチ
- `feature/<topic>`: 機能開発と仕様策定
- 変更は可能な限りPull Requestを通して `main` へ統合する

GitHub Actionsは `main` と `feature/**` へのpush、`main` 向けPull Request、手動実行で次を並列に検証します。

- 単体テスト
- Debug / Release Android Lint
- Debug / Release APKビルド
- Benchmark APKビルド
- 署名ファイル、秘密鍵、アクセストークンの混入検査

Debug、Release、Benchmarkおよびリポジトリ検査を独立ジョブで実行し、最後に `Test, lint, and build` へ結果を集約します。同じfeatureブランチでpushとPull RequestのCIが重なった場合は古い実行を自動キャンセルし、二重のフル実行を防ぎます。

## 依存関係の更新

依存バージョンはモジュールごとの`gradle.lockfile`で固定し、取得した成果物は
`gradle/verification-metadata.xml`のSHA-256で検証します。依存を更新した場合は、
次のコマンドでロックと検証メタデータを更新し、差分に意図しない成果物がないことを確認します。

```powershell
.\gradlew.bat :app:dependencies :benchmark:dependencies --write-locks
.\gradlew.bat testDebugUnitTest lintDebug :app:lintRelease :app:assembleRelease :app:assembleBenchmark :benchmark:assembleBenchmarkRelease --write-verification-metadata sha256
```

## 機密情報

署名鍵、`keystore.properties`、`local.properties` はコミットしません。Release署名と広告サービスの識別子は、導入時にGitHub Secretsまたはローカルの非追跡ファイルから注入します。

## 仕様

- [アプリ全体仕様](.agents/app-spec.md)
- [画面仕様一覧](.agents/screen-specs/README.md)
- [開発ガイドライン](.agents/development-guidelines.md)
