# Dependabot更新2件の統合検証（2026-11-04）

- 対象: AGP 9.5.0、DataStore 1.3.0への通常更新
- ブランチ: `feature/dependabot-maintenance-20261104`
- 基点: main `6df9309ca6f080b862dfe3031f3b2d29b5decae3`（カテゴリ並べ替え修正のPR #346を含む）
- 方針: 元PRのcommitを統合し、必須CI・レビュー会話・最新baseを確認して通常マージする。修正版アプリの配布は別作業
- 公開版: [1.0.0 (11)](production-candidate-1.0.0-11.md)の保管済みAABは更新しない

## 1. 対象と失敗原因

| 元PR | 更新 | 取り込んだ元head |
| --- | --- | --- |
| [#339](https://github.com/Sakemotti/MATA/pull/339) | AGP 9.4.1 → 9.5.0 | `a79ac7a474db1380a3b9701d208cd74521ee2fa0` |
| [#340](https://github.com/Sakemotti/MATA/pull/340) | DataStore 1.2.1 → 1.3.0 | `195750af9e5f8895c5af4db88807c542301f12f6` |

元PRのCIは、新版plugin・POM・module等のSHA-256が検証メタデータへ未登録のため失敗していた。過去の失敗・CodeQLのNEUTRALを成功へ読み替えず、最新mainへ2件を統合した構成で全CIを実行する。元PRの包含とMERGED状態は、統合PRのマージ後に確認する。

## 2. 互換性・依存情報

- AGP 9.5.0の最低条件はGradle 9.6.0・JDK 17。既存Wrapper 9.8.2とローカルのdaemon JDK 21を維持する。
- Android Studio Quail 4のAGP対応上限は9.4であり、9.5にはRabbit 2以降が必要。2026-11-04にUSERがIDEを自身で更新する前提で2件とも進めることを了承した。IDE自体の更新や設定変更は行わず、更新完了も補完しない。
- 標準Gradleタスクで両モジュールのロックと検証メタデータを再生成した。SBOM・ライセンス生成が別途取得するPOMも登録し、CI用Linux AAPT2も個別に確認した。
- 既存artifact 2,747件のSHA-256集合と検証設定は不変。artifactエントリ156件を追加し、信頼ワイルドカードの拡張や`strict`の無効化は行わない。
- AGPのplugin POM 2件、AGP本体JAR、DataStore PreferencesのPOM、AAPT2のWindows／Linux JAR・POMの計7件は、Google公式Mavenの公開SHA-256と取得内容・登録値の一致を確認した。
- 統合PR [#347](https://github.com/Sakemotti/MATA/pull/347)の[初回CI](https://github.com/Sakemotti/MATA/actions/runs/41815406742)では、キャッシュのない環境が取得する`org.bouncycastle:bc-jdk18on-bom:1.80.2`のmoduleメタデータ1件が未登録で失敗した。Maven Centralの公開SHA-256・内容・座標を照合して追加し、初回の失敗は履歴として保持する。公式チェックサムとの追加照合は上記7件と合わせて8件。
- DataStoreに伴うCollection 1.6.0、Tracing 2.0.1、AtomicFU 0.28.0、Okio 3.17.0等の推移依存と、AGPのLint・テストエンジン等のロック更新を確認する。新しいWeb保存・Tink暗号化・Builder APIをアプリへ導入する変更ではない。

## 3. 検証の扱い

署名資格情報はコマンドラインの空値で上書きし、`MATA_REQUIRE_UPLOAD_SIGNING=false`・Configuration Cacheなしの検証構成を使用する。秘密値の読出し・出力や公開候補の再署名は行わない。

初回の全構成同時検証はローカルPCのネイティブメモリ不足でdaemonが停止した。生成・ビルドを分割し、実行コマンドだけでワーカー2・並列実行なし・JVM上限3GBとCPU数2に制限して再確認する。プロジェクト、ユーザーのGradle設定、IDE設定やCIの検査条件は変更しない。生成されたクラッシュログはGit除外の`app/build/reports/dependabot-maintenance-20261104/`へ保管する。

書出しオプションなしの`--dependency-verification strict`でDebug・Release・Performanceの各構成、単体試験・Lint・署名／法的URLガードを検証する。API 30のinstrumented test、Release成果物検査とCodeQLは統合PRの必須CIで確認する。既存P0/P1 405件の実績を今回の全件再実施や実機更新確認として扱わない。

## 4. 公開版との境界

公開版11のAAB SHA-256が`3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE`のまま不変であることを再照合した。今回のビルド出力を保管済みAAB・ZIPへ複製せず、署名・広告ID・公開タグも変更しない。

次回配布には、全トラックの登録済み最大番号より大きいversionCodeの別候補が必要。カテゴリ並べ替え、設定の永続化、バックアップ／復元と主要操作の差分回帰を含めて確認する。IDE更新、修正版配布、AdMob審査結果・実広告確認と公開後監視を、依存更新のCI成功だけで完了にしない。

## 5. 公式資料

- [AGP 9.5.0リリースノート・互換性](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- [Android StudioとAGPの互換表](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility)
- [DataStore 1.3.0リリースノート](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0)
- [Gradle Dependency Verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
