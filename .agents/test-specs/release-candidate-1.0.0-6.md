# MATA 1.0.0 (6) 公開候補生成結果

- 試験ID: `REL-021`
- 状態: versionCode `6`設定済み。署名済み成果物は未生成
- 実施日: `-`
- 実施者: `OWNER` / `AUTO`
- 判定: `未実施`
- 公開承認: `未承認`
- 生成計画: [MATA 1.0.0 (6) 本番公開候補生成計画](production-release-candidate-1.0.0-6-plan.md)
- 回帰試験結果: [MATA 1.0.0 (6) 回帰試験実施票](version-6-regression-results.md)

本書はversionCode `6`の成果物を生成する前に用意した台帳である。`未生成`、`未実施`または`未確認`を推測値で置き換えない。コマンド出力、成果物メタデータ、CI、Google Play Consoleまたは実機で確認した値だけを記録する。

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `6`（設定済み。成果物未生成） |
| ソースcommit | 未確定 |
| ソースブランチ | クリーンな`main`を使用予定 |
| `origin/main`との一致 | 未確認 |
| ビルド日時 | 未生成 |
| ビルドOS | 未記録 |
| JDK / Gradle / AGP | 未記録 |
| 署名方法 | Upload Key予定 |
| 署名者数 | 未確認 |
| `publishable` | 未確認 |
| Upload Key SHA-256 | 期待値 `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D`。成果物照合は未実施 |
| ローカル保管先 | `app/release/1.0.0-6/`予定。Git除外対象 |

生成開始前に、[差分・回帰試験計画](version-6-delta-and-regression-plan.md)の棚卸し対象commit以降を再確認する。本番コードまたは同梱成果物に追加差分がある場合は回帰範囲へ反映する。

## 2. 生成コマンド

Upload Keyの秘密値はリポジトリ外のGradleユーザープロパティから読み込む。実行前後に秘密値、keystoreパスおよび署名情報がログや追跡ファイルへ混入していないことを確認する。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
node tools/release/verify-readiness.mjs --release
```

実行結果:

- Gradle終了結果: `未実施`
- Release準備検査: `未実施`
- 実行ログ保管先: `未記録`

## 3. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 | 検証 |
| --- | --- | ---: | --- | --- |
| Android App Bundle | `mata-1.0.0-6.aab`予定 | 未生成 | 未生成 | 未実施 |
| R8 mapping | `mapping.txt`予定 | 未生成 | 未生成 | 未実施 |
| オープンソースライセンス | `aboutlibraries.json`予定 | 未生成 | 未生成 | 未実施 |
| 最終Manifest | `AndroidManifest.xml`予定 | 未生成 | 未生成 | 未実施 |
| CycloneDX SBOM | `release-sbom.cdx.json`予定 | 未生成 | 未生成 | 未実施 |

同じディレクトリへ`release-metadata.json`と`release-readiness.json`を保存し、5成果物の容量、SHA-256、ソースcommit、versionCode、ビルド日時、署名結果を対応付ける。秘密値とkeystoreのパスは記録しない。

## 4. 自動ゲート・CI

| ゲート | 結果 | 証跡 |
| --- | --- | --- |
| Repository security | 未実施 | - |
| Debug test・Lint・build | 未実施 | - |
| Release Lint・AAB・成果物検査 | 未実施 | - |
| API 30 instrumented test | 未実施 | - |
| Performance / Benchmark | 未実施 | - |
| P0/P1 405件・DEV_AUTO証跡 | 未実施 | - |
| Play掲載成果物検査 | 未実施 | - |
| Release準備検査 | 未実施 | - |

- versionCode設定PR: `未作成`
- PR CI run: `未実施`
- main CI run: `未実施`
- 必須チェック総合結果: `未実施`

## 5. 署名・ネイティブコード

| 項目 | 実績値 |
| --- | --- |
| AAB署名者数 | 未確認 |
| AAB証明書SHA-256 | 未確認 |
| Play ConsoleのUpload Keyとの一致 | 未確認 |
| Play App Signing | 有効、versionCode 5まで確認済み。versionCode 6は未確認 |
| Native library ABI・件数 | 未確認 |
| Native Debug Symbols | 未確認 |

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
| リリースノート | `fastlane/metadata/android/ja-JP/changelogs/6.txt` | 未照合 |
| 短い説明 | `fastlane/metadata/android/ja-JP/short_description.txt` | 未照合 |
| 詳細な説明 | `fastlane/metadata/android/ja-JP/full_description.txt` | 未照合 |
| ストア画像 | `fastlane/metadata/android/ja-JP/images/` | 未照合 |
| プライバシーポリシー | リポジトリ正本と公開サイト | 未照合 |
| 利用規約 | リポジトリ正本と公開サイト | 未照合 |
| 外部送信公表 | リポジトリ正本と公開サイト | 未照合 |
| `app-ads.txt` | リポジトリ正本と公開サイト | 未照合 |

## 8. 保管・復旧

- [ ] AAB、mapping、Manifest、SBOM、ライセンス一覧、メタデータおよび検査結果を同じversionCodeへ紐付けた
- [ ] AABとmappingをアクセス制限された安全な別保管先へ保存した
- [ ] Upload Keyのkeystoreと復旧情報の暗号化バックアップを再確認した
- [ ] 保管物のSHA-256を再計算し、第3節と一致した
- [ ] Git commit、PR、CI、Play Console登録物およびローカル成果物を一意に追跡できる

## 9. 公開判定

| 判定項目 | 状態 |
| --- | --- |
| Closed testingの12人・14日間要件 | 2026年9月23日に達成済み |
| Production access | 2026年9月23日に申請済み・審査中。承認待ち |
| versionCode 6自動ゲート10件 | 未実施 |
| versionCode 6実機回帰18件 | 未実施 |
| versionCode 6 Play確認7件 | 未実施 |
| P0/P1 405件 | 現行結果は合格。versionCode 6候補での維持確認は未実施 |
| 未解決S0・S1 | 未確認 |
| 成果物ハッシュ・署名 | 未確認 |
| Production移行 | 不可 |

- 最終判定: `未実施`
- 判定日: `-`
- 判定者: `-`
- 公開対象commit: `未確定`
- 公開後Gitタグ: `v1.0.0`予定、未付与
- 備考: versionCode `6`をGoogle Playへ一度でも登録した後にバイナリ変更が必要になった場合は、versionCode `7`以上で候補を作り直す
