# MATA 1.0.0 (6) 公開候補生成結果

- 試験ID: `REL-021`
- 状態: 署名済み公開候補生成・自動検証済み。実機回帰、Google Play登録後確認およびProduction access承認待ち
- 実施日: `2026-09-23`
- 実施者: `OWNER` / `AUTO`
- 判定: `候補生成合格`。Production移行は保留
- 公開承認: `未承認`
- 生成計画: [MATA 1.0.0 (6) 本番公開候補生成計画](production-release-candidate-1.0.0-6-plan.md)
- 回帰試験結果: [MATA 1.0.0 (6) 回帰試験実施票](version-6-regression-results.md)

本書はversionCode `6`の成果物を生成する前に用意し、生成後に実績値を追記した台帳である。`未実施`または`未確認`を推測値で置き換えない。コマンド出力、成果物メタデータ、CI、Google Play Consoleまたは実機で確認した値だけを記録する。

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `6` |
| ソースcommit | `4bdb50bbef2f3ed15ebcb7e0a69199d53e5e6757` |
| ソースブランチ | クリーンな`main` |
| `origin/main`との一致 | 生成時に一致 |
| ビルド日時 | `2026-09-23T04:04:56.731361500Z`（日本時間2026年9月23日13:04） |
| ビルドOS | Windows 11 Home 64-bit `10.0.26200` |
| JDK / Gradle / AGP | OpenJDK `25.0.3` / Gradle `9.7.1` / AGP `9.4.1` |
| 署名方法 | Upload Key |
| 署名者数 | 1件 |
| `publishable` | `true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D`。期待値と一致 |
| ローカル保管先 | `app/release/1.0.0-6/`。Git除外対象 |

生成開始前に、[差分・回帰試験計画](version-6-delta-and-regression-plan.md)の棚卸し対象commit以降を再確認する。本番コードまたは同梱成果物に追加差分がある場合は回帰範囲へ反映する。

## 2. 生成コマンド

Upload Keyの秘密値はリポジトリ外のGradleユーザープロパティから読み込む。実行前後に秘密値、keystoreパスおよび署名情報がログや追跡ファイルへ混入していないことを確認する。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
node tools/release/verify-readiness.mjs --release
```

実行結果:

- Gradle終了結果: `BUILD SUCCESSFUL`。64タスク中45件実行、17件cache、2件up-to-date
- Release準備検査: 全7検査成功。5成果物のハッシュ一致、runtime component 226件、Upload Key署名を確認
- 実行ログ・検査結果: ローカルコンソールと`app/release/1.0.0-6/release-readiness.json`

## 3. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 | 検証 |
| --- | --- | ---: | --- | --- |
| Android App Bundle | `mata-1.0.0-6.aab` | 12,905,674 | `59133b8f7707dc960e0808436c0c8e13faf12e06e71da7fd8fed0f6c55cde9a4` | 合格 |
| R8 mapping | `mapping.txt` | 89,253,703 | `d19c0d9f526b2d9d9d9bc578c19e8f44a3949b732e6ab74f51277b12c57d1165` | 合格 |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `5671e2c8c228e18b5e4f5af08c0b330b6f989393286fb625a83a6ef314cc6c6f` | 合格 |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `5ce916b7143dfaaf507e3a4163cc32b099f15660765ed118e7bf527ce671946c` | 合格 |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,847 | `a632477178c655b2104e741ed72483074ca95e1268dcef69ff539fb5614a8638` | 合格 |

同じディレクトリへ`release-metadata.json`と`release-readiness.json`を保存した。ローカル複製後に5成果物の容量とSHA-256を再計算し、メタデータとの完全一致を確認した。両JSONに署名秘密とkeystoreのパスは含まれない。

## 4. 自動ゲート・CI

| ゲート | 結果 | 証跡 |
| --- | --- | --- |
| Repository security | 合格 | [main Android CI run 35816296254](https://github.com/Sakemotti/MATA/actions/runs/35816296254) |
| Debug test・Lint・build | 合格 | main Android CI run 35816296254 |
| Release Lint・AAB・成果物検査 | 合格 | main Android CI run 35816296254、ローカル署名済み成果物検査 |
| API 30 instrumented test | 合格 | [PR Android CI run 35815319699](https://github.com/Sakemotti/MATA/actions/runs/35815319699) |
| Performance / Benchmark | 合格 | main Android CI run 35816296254 |
| P0/P1 405件・DEV_AUTO証跡 | 合格 | main Android CI run 35816296254 |
| Play掲載成果物検査 | 合格 | `verify-readiness.mjs --release` |
| Release準備検査 | 合格 | `app/release/1.0.0-6/release-readiness.json` |

- versionCode設定PR: [#266](https://github.com/Sakemotti/MATA/pull/266)。2026年9月23日マージ済み
- PR CI run: [Android CI 35815319699](https://github.com/Sakemotti/MATA/actions/runs/35815319699)。全ジョブ成功
- main CI run: [Android CI 35816296254](https://github.com/Sakemotti/MATA/actions/runs/35816296254)、[CodeQL 35816296273](https://github.com/Sakemotti/MATA/actions/runs/35816296273)。全ジョブ成功
- 必須チェック総合結果: 合格

## 5. 署名・ネイティブコード

| 項目 | 実績値 |
| --- | --- |
| AAB署名者数 | 1件 |
| AAB証明書SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| Play ConsoleのUpload Keyとの一致 | 一致。versionCode 5までのConsole確認値と同一 |
| Play App Signing | 有効、versionCode 5まで確認済み。versionCode 6登録後に再確認する |
| Native library ABI・件数 | `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`の4 ABI、合計8件 |
| Native Debug Symbols | 依存元ですでに除去されている2ライブラリを各ABIへ収録。取得可能なシンボルなし。既知警告をConsole登録後に再確認する |

依存ライブラリ側ですでにシンボルが除去されている場合、Native Debug Symbols未登録の既知警告が継続する可能性がある。空または架空のシンボルファイルを作成せず、実際のAABに対するPlay Consoleの表示を記録する。

## 6. Google Play登録

同じAABをInternal testingで検証後、再ビルドせずProductionへ使用する。各段階でAAB SHA-256を第3節と照合する。

| 項目 | Internal testing | Production |
| --- | --- | --- |
| 登録日時 | 未登録 | 未登録 |
| リリース状態 | 未登録 | 未登録 |
| AAB SHA-256一致 | 未確認 | 未確認 |
| リリースノート | 未確認 | 未確認 |
| 新規警告 | 未確認 | 未確認 |
| Pre-launch report | 未生成 | 未生成 |
| SDK Index・ポリシー | 未確認 | 未確認 |
| Google Play更新・新規インストール | 未実施 | 未実施 |

## 7. ストア・法的成果物

| 項目 | 正本 | 結果 |
| --- | --- | --- |
| リリースノート | `fastlane/metadata/android/ja-JP/changelogs/6.txt` | 検査合格 |
| 短い説明 | `fastlane/metadata/android/ja-JP/short_description.txt` | 検査合格 |
| 詳細な説明 | `fastlane/metadata/android/ja-JP/full_description.txt` | 検査合格 |
| ストア画像 | `fastlane/metadata/android/ja-JP/images/` | 検査合格 |
| プライバシーポリシー | リポジトリ正本と公開サイト | 検査合格 |
| 利用規約 | リポジトリ正本と公開サイト | 検査合格 |
| 外部送信公表 | リポジトリ正本と公開サイト | 検査合格 |
| `app-ads.txt` | リポジトリ正本と公開サイト | 検査合格 |

## 8. 保管・復旧

- [x] AAB、mapping、Manifest、SBOM、ライセンス一覧、メタデータおよび検査結果を同じversionCodeへ紐付けた
- [ ] AABとmappingをアクセス制限された安全な別保管先へ保存した
- [x] Upload Keyのkeystoreと復旧情報の暗号化バックアップを再確認した
- [x] 保管物のSHA-256を再計算し、第3節と一致した
- [ ] Git commit、PR、CIおよびローカル成果物は一意に追跡できる。Play Console登録物は未登録

## 9. 公開判定

| 判定項目 | 状態 |
| --- | --- |
| Closed testingの12人・14日間要件 | 2026年9月23日に達成済み |
| Production access | 2026年9月23日に申請済み・審査中。承認待ち |
| versionCode 6自動ゲート10件 | 10/10件合格 |
| versionCode 6実機回帰18件 | 未実施 |
| versionCode 6 Play確認7件 | 未実施 |
| P0/P1 405件 | versionCode 6候補で405/405件合格を維持 |
| 未解決S0・S1 | 0件 |
| 成果物ハッシュ・署名 | 合格 |
| Production移行 | 不可 |

- 最終判定: `候補生成合格`。実機回帰・Play登録後確認・Production access承認待ち
- 判定日: `2026-09-23`
- 判定者: `AUTO`
- 公開対象commit: `4bdb50bbef2f3ed15ebcb7e0a69199d53e5e6757`
- 公開後Gitタグ: `v1.0.0`予定、未付与
- 備考: versionCode `6`をGoogle Playへ一度でも登録した後にバイナリ変更が必要になった場合は、versionCode `7`以上で候補を作り直す
