# MATA 1.0.0 (8) Closed testing公開候補生成結果

- 状態: Upload Key署名済み候補生成・自動検証・ローカル保管完了／Google Play登録・更新・テスター再確認待ち
- 実施日: 2026-10-08
- 実施者: OWNER / AUTO
- 判定: Closed testing候補生成合格。Production公開の承認ではない
- 登録手順: [MATA 1.0.0 (8) Closed testing登録・更新確認手順](closed-testing-release-1.0.0-8.md)

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `8` |
| ソースcommit | `82f998d1d6505862508a43e8854832b1717962cc` |
| ソースブランチ / 作業ツリー | `main` / クリーン |
| `origin/main`との一致 | 生成時とGitHubゲート検査時に一致 |
| ビルド日時（UTC） | `2026-10-08T01:39:52.862447500Z` |
| 署名方法 / 署名者数 | Upload Key / 1件 |
| `publishable` | `true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |

versionCode設定と登録手順の[PR #287](https://github.com/Sakemotti/MATA/pull/287)を全CI成功後にマージし、そのmain commitから生成した。
本書等の生成結果記録だけを追加する後続commitは、AABのソースcommitを変更しない。生成済みAABを再ビルドや置換せず、以下のハッシュと照合して使用する。

## 2. 公開用AAB

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/1.0.0-8/mata-1.0.0-8.aab` |
| 容量 | `13,039,573 bytes` |
| SHA-256 | `421BA79A0C641AFDBBEE164921259E103CBC16B9D4CABCB0070AFDAFB65EF3C5` |
| Google Play登録状態 | 未登録。現在の公開版はversionCode `7` |

Google PlayのAAB欄へ登録するファイルは上表のAABだけとする。
`app/release/`はGit除外対象であり、成果物、署名鍵および秘密値を本リポジトリへ追加していない。

## 3. 成果物と保管

以下を`app/release/1.0.0-8/`へ複製した。

| 種別 | ファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-8.aab` | 13,039,573 | `421BA79A0C641AFDBBEE164921259E103CBC16B9D4CABCB0070AFDAFB65EF3C5` |
| R8 mapping | `mapping.txt` | 90,224,633 | `51159D632CFBD6B7F5D0FB9DF1814447E1BCA1C7012D49E4406E4092FECB2598` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `3E0EDD2CC617315190A1C2E603D3A72570B82EF9F2727A5ECD13E4C17C12C70B` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `055A49A6B0C73384DC3E3789392EFF6E75175936A8194B7A0337A62CDB6CF571` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,927 | `66CECCB122033E3C0C970C0C5E67785319A0CA0DF02B364712F8AEF0AA963182` |
| Releaseメタデータ | `release-metadata.json` | 1,857 | `12D14448A13683E600230EE10829DD56BDC08DEDC8195B66877080C3A890EFD5` |
| Release事前検査 | `release-readiness.json` | 1,156 | `1E467B8D93778FAC48200F7349356E2D6F17F67FF1E2D9DC7D213855EB937837` |
| GitHubゲート検査 | `github-release-gates.json` | 3,413 | `DE22250427CB085F4F5E6612AA0FE4C9EC4A414F2B9675980EA8C5535DB6B0E7` |

5成果物は生成元メタデータと複製後の容量・SHA-256が一致した。3証跡も生成元と複製後のSHA-256が一致した。
以前のversionCode `7`等の保管物は上書きしていない。

| ZIP項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/MATA-1.0.0-8-release-candidate.zip` |
| 容量 | `19,265,826 bytes` |
| SHA-256 | `397F8C69A6626A86558DE29CFA5B189117658E9EB0DC1FFA4133700392D8033F` |
| 収録ファイル数 | 8 |
| 内容検査 | 8ファイルすべてをZIP内から読み、保管元とSHA-256が一致 |

ZIPは保管・受け渡し専用であり、Google Playへアップロードしない。

## 4. 自動検証

- [x] PR #287の[Android CI run 37712733228](https://github.com/Sakemotti/MATA/actions/runs/37712733228)が成功。単体試験、Debug Lint・ビルド、Release、Performance APK、API 30 instrumented test、Repository securityおよび最終集約が成功した。
- [x] PRの[CodeQL run 37712731240](https://github.com/Sakemotti/MATA/actions/runs/37712731240)が成功した。
- [x] 生成元mainの[Android CI run 37713981531](https://github.com/Sakemotti/MATA/actions/runs/37713981531)と[CodeQL run 37713981879](https://github.com/Sakemotti/MATA/actions/runs/37713981879)が成功した。
- [x] `:app:generateReleaseArtifactMetadata :app:verifyReleaseManifestSecurity :app:verifyUploadSigningGuards -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon`が成功。署名設定の異常系5件を拒否した。
- [x] `node tools/release/verify-readiness.mjs --release`の全7検査が合格。5成果物のハッシュ、226 runtime component、単一Upload Key署名者および期待する証明書SHA-256が一致した。
- [x] `node tools/release/verify-github-release-gates.mjs`が合格。生成元mainとの一致、クリーンな作業ツリー、未解決Issue・PR各0件、ブランチ保護、必須チェック、セキュリティ機能、open alert各0件およびCI成功を確認した。
- [x] リリース検証ツールの回帰試験、試験実施区分424件、自動試験との1対1対応336件、アーキテクチャ検査が合格した。
- [x] 既存の試験結果台帳でP0/P1 405/405件の合格を確認した。versionCode `8`のPlay経由更新・実機・テスター再確認はまだ実施していない。

GitHubゲートではCodeQL Java/Kotlin未対応だけを既知の警告として記録した。Android CIの検査は省略していない。

## 5. 前回版との比較・最適化

- versionCode `7`とのSBOM比較で、226 runtime componentのpurlに追加・削除・変更は0件だった。
- オープンソースライセンス一覧のSHA-256はversionCode `7`と同一だった。
- 最終Manifestは`android:versionCode`を`7`から`8`へ置き換えると完全一致し、新しい権限・コンポーネントはない。
- DBスキーマとバックアップ形式にソース差分はない。
- AABにBaseline Profileの`baseline.prof`（12,034 bytes）と`baseline.profm`（1,516 bytes）を含む。
- AABに4 ABI合計8件のネイティブライブラリを含み、取得可能なNative Debug Symbolsは0件だった。依存元でシンボル除去済みの既知制約は維持される。

## 6. 残りの確認と判定

候補生成は合格とし、[登録・更新確認手順](closed-testing-release-1.0.0-8.md)に従ってClosed testingへ登録する。

- Google Play登録・公開、Console警告、Pre-launch report、SDK Indexおよびポリシー状態は確認待ちである。
- versionCode `7`→`8`の上書き更新・データ保持、配置済みと新規ウィジェット、土日祝日の色、テーマ別表示および主要機能のスモーク確認は未実施である。
- Issue #281・#284の開発版表示確認を、配布後のテスター再確認として扱わない。
- ネイティブデバッグシンボル未登録警告が表示される可能性がある。架空・空のシンボルを登録せず、Consoleで実際の警告を記録する。
- Productionへ自動昇格せず、Production access再申請と承認後の公開判定を別途行う。
