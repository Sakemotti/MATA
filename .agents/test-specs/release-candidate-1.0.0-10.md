# MATA 1.0.0 (10) Closed testing候補生成結果

- 状態: 公開済み／OWNERのデータ保持・回帰確認と原報告者本人の追加機能再確認は問題なし（USER報告）
- 実施日: 2026-10-19
- 最終更新日: 2026-10-25
- 実施者: AUTO
- 判定: Closed testing候補の技術的検査は合格。本番向けGitHubゲートは未解決Issue・PRにより未合格であり、Production公開承認ではない
- 登録手順: [MATA 1.0.0 (10) 登録・更新確認手順](closed-testing-release-1.0.0-10.md)

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `10` |
| ソースcommit | `5728f4fd2ceb0867150b699bea960634abcdb92c` |
| ソースブランチ / 作業ツリー | 生成時は`main` / クリーン |
| ビルド日時（UTC） | `2026-10-19T02:22:35.541221500Z` |
| 署名方法 / 署名者数 | Upload Key / 1件 |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| `publishable` | `true`。署名条件の技術的判定であり、Production公開承認ではない |

[PR #313](https://github.com/Sakemotti/MATA/pull/313)を全CI成功後に通常マージし、そのmain commitから生成した。本書等の生成記録だけを追加する後続commitでAABの生成元を変更しない。生成済みAABを再ビルド・置換せず、登録前に以下のハッシュと照合する。

## 2. アップロード用AAB

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/1.0.0-10/mata-1.0.0-10.aab` |
| 容量 | `13,084,152 bytes` |
| SHA-256 | `2203C2F7934D36CAA63D98412DFB097E626661EB8BC56B056A53908F12EDCB99` |
| 配信先 | 現在運用中の新しいClosed testingトラック |
| Consoleアップロード・公開 | アップロード済み・既知の警告のみ（2026-10-19 USER報告、CT-024）。続いて公開済みとUSER報告（CT-025）。正確なConsole状態文言・実際の公開日時は未記録 |

Google Playへ登録するのは上記AABだけであり、保管用ZIPやmappingをAAB欄へアップロードしない。`app/release/`はGit除外対象で、生成物・keystore・秘密値はコミットしていない。

## 3. 成果物と保管

以下を`app/release/1.0.0-10/`へ複製した。

| 種別 | ファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-10.aab` | 13,084,152 | `2203C2F7934D36CAA63D98412DFB097E626661EB8BC56B056A53908F12EDCB99` |
| R8 mapping | `mapping.txt` | 90,770,297 | `4AC596DAA3C62CC71C86022A756EE23C77D4D6EA6B253F647A67AE6627465181` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `E8BCD0C2479D767DE785A2F2804FAE00B43C1F8D7CE64577E60E6CA6624FB7F3` |
| 最終Manifest | `AndroidManifest.xml` | 13,462 | `52A70CCEFE2775E0D8D885FA6BA5973C6B738048919F47A798ED3F642702D1AF` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,927 | `C814DB5F172F158B3BEE0B42CC6719ED82A7982E9BFDBE8FB5FB3A4D84D500CC` |
| Releaseメタデータ | `release-metadata.json` | 1,858 | `6A46EF497DE26920CAD4945522F4B9C5E02B8678C546F4F1928691B9403BD8E2` |
| Release事前検査 | `release-readiness.json` | 1,158 | `CCB54F735FE45B8509CE10E9A9F46E947F980CBD1433595262B278C3F78F514F` |
| GitHubゲート検査 | `github-release-gates.json` | 3,413 | `00E355E09F45C3209A277AB91AFC39BBCB5103B7ABED2AC4FBA0A0707C64D8EC` |

5成果物は生成元メタデータと、3証跡は生成元ファイルと、それぞれ複製後の容量・SHA-256が一致した。GitHubゲートの`failed`を変更せず保管し、versionCode 9以前の保管物は上書きしていない。

| ZIP項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/MATA-1.0.0-10-release-candidate.zip` |
| 容量 | `19,343,177 bytes` |
| SHA-256 | `77AD7BEF47951366B1C575663E52427E9EFFDD51F38E4B544C20FA495189C2FC` |
| 収録ファイル数 | 8 |
| 内容検査 | 全8ファイルをZIP内から読み、保管元と容量・SHA-256が一致 |

保管スクリプトの初回実行はWindowsの実行ポリシーにより拒否された。USERの許可後、そのスクリプトを実行する子プロセスだけに一時的な例外を指定して保管した。恒久的な実行ポリシーは変更していない。

## 4. 自動検証と本番向けゲート

- [PRのAndroid CI](https://github.com/Sakemotti/MATA/actions/runs/39188776855)と[CodeQL](https://github.com/Sakemotti/MATA/actions/runs/39188774571)は全成功。単体試験・Lint・Debug／Release／Performance APK・API 30 instrumented testを含む。
- 生成元mainの[Android CI](https://github.com/Sakemotti/MATA/actions/runs/39189646689)と[CodeQL](https://github.com/Sakemotti/MATA/actions/runs/39189646310)も成功した。
- 設定済みUpload Keyを用いて`:app:lintRelease :app:generateReleaseArtifactMetadata :app:verifyReleaseManifestSecurity :app:verifyUploadSigningGuards`を、署名必須・strict依存関係検証・Configuration Cacheなしで実行した。中断後にもLint・Manifest・署名ガードを再実行し、成功した。生成済みAABは再生成していない。
- `node tools/release/verify-readiness.mjs --release`の全7検査が合格。クリーンなmain、署名、5成果物のハッシュ、226 runtime component、法的文書とストア掲載物の整合性を確認した。
- 生成されたRelease設定の広告App ID・バナーID・法的URLが既存の正式設定と一致した。
- 成果物検証ツールの回帰試験と、署名設定の異常系5件も合格した。
- GitHubゲートの生成時判定は`failed`。未解決Issue 1件（[#306](https://github.com/Sakemotti/MATA/issues/306)、過去日操作の仕様に関する報告条件確認待ち）と、通常依存関係PR 2件（[#310](https://github.com/Sakemotti/MATA/pull/310)、[#311](https://github.com/Sakemotti/MATA/pull/311)）を維持したためである。形式的なクローズ、保護の回避、検査結果の書換えは行っていない。
- 同ゲートのmain一致、作業ツリー、ブランチ保護、必須チェック、セキュリティ機能、open alert各0件、生成元CIは合格した。CodeQL Java/Kotlin未対応の警告は維持する。

本機能の[追加確認記録](incomplete-todos-309.md)にあるUSER動作確認、単体240件とDB・UI126件は実装時の証跡として参照する。配布版の確認は後述のCT-026と分ける。これらの自動検証からCT10-01〜CT10-11や従来の全P0/P1再実施の合格を補完しない。

## 5. 前回版との比較・署名診断

- versionCode 9とのSBOM比較では、226 runtime componentのpurlに追加・更新・削除はなかった。ライセンス一覧も同じ容量・SHA-256だった。
- 最終ManifestはversionCodeを9→10へ置き換えると完全一致した。権限・exported component等の追加はない。
- アプリの変更は未完了TODO一覧とレイアウト改善を含み、Roomは8→9、バックアップ形式は5→6となる。旧形式1〜5の読込を維持するが、形式6を旧アプリでは復元できない。
- AABにBaseline Profileの`baseline.prof`（11,905 bytes）と`baseline.profm`（1,496 bytes）を含む。
- AABにネイティブライブラリ8件を含み、取得可能なNative Debug Symbolsは0件だった。2026-10-19にUSERからアップロード時の警告は既知のもののみと報告された。今回の正確な文言・件数は未記録である。
- JBRの`jarsigner -verify`は終了コード0・`jar verified.`だった。自己署名、信頼チェーン、タイムスタンプなし、POSIX属性およびJarFile／JarInputStreamの差異に関する警告も表示された。versionCode 9の記録にも同種の診断がある。Gradle側のJarFile検証と単一署名者・期待証明書の一致を確認し、署名検証の無効化・AABの並べ替え・再署名は行っていない。Play Consoleでの受理を先取りしない。

## 6. 引き渡しと残りの確認

2026-10-19にUSERからAABのアップロード済み・既知の警告のみとの報告を受領した（CT-024）。続いて公開済み（CT-025）、データ保持・回帰確認は問題なし（CT-026）と報告された。[登録・更新確認手順](closed-testing-release-1.0.0-10.md)のCT10-01〜CT10-10はOWNERの一括報告として合格を記録する。実際の公開・確認日時、正確なConsole状態文言、端末・OS・条件別所見は未記録であり、報告日や前版の情報で補完しない。

2026-10-25にR3-T11（原報告者本人）が`1.0.0-10`で未完了TODO一覧の追加機能を再確認して問題なしとUSERが報告した（CT10-11・CT-027）。端末・Androidは不明、操作別所見と具体的な報告経路は未記録。OWNER結果とは別のテスター確認として記録し、従来の全P0/P1再実施やGoogle Playへの新しいFB投稿へ広げない。

継続参加・利用とフィードバックを追跡し、Production accessと本番向けゲートは別途判定する。生成時のゲートJSON・AAB・ZIPを変更せず、Productionへ自動昇格しない。
