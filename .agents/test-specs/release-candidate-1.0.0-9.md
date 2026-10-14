# MATA 1.0.0 (9) Closed testing候補生成結果

- 状態: 生成・署名・検証・保管・2026-10-14公開済み／8→9更新とデータ保持・原報告者改善再確認・OWNER回帰6件の確認完了
- 実施日: 2026-10-14
- 実施者: AUTO
- 判定: Closed testing候補の生成・署名・Release事前検査は合格。生成時のProduction向けGitHubゲートは未合格の歴史証跡として維持し、現在の本番公開判定は未実施
- 登録手順: [MATA 1.0.0 (9) Closed testing登録・更新確認手順](closed-testing-release-1.0.0-9.md)

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `9` |
| ソースcommit | `53bb2764e935c13430e3360460032b6dd48c9007` |
| ソースブランチ / 作業ツリー | 生成時は`main` / クリーン |
| GitHub mainとの一致 | 生成元のGitHubゲート検査時に一致 |
| ビルド日時（UTC） | `2026-10-14T02:11:48.876285100Z` |
| 署名方法 / 署名者数 | Upload Key / 1件 |
| `publishable` | `true`。署名条件を満たすという技術的判定であり、Production公開承認ではない |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |

versionCode設定と登録手順の[PR #301](https://github.com/Sakemotti/MATA/pull/301)を全CI成功後に手動マージし、そのmain commitから生成した。本書等の生成結果記録だけを追加する後続commitは、AABのソースcommitを変更しない。生成済みAABを再ビルド・置換せず、以下のハッシュと照合して使用する。

## 2. アップロード用AAB

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/1.0.0-9/mata-1.0.0-9.aab` |
| 容量 | `13,039,823 bytes` |
| SHA-256 | `C619CA30F6747F408F56F15CA3120382DA384F5018D1DFBBD145C7130256E461` |
| 配布・使用確認 | 2026-10-14公開済み、既存の警告のみ・新規警告なし（USER報告）。公開時刻は未記録。原報告者改善再確認とOWNERの8→9更新・データ保持も確認済み |
| 配信対象 | 新規募集に使用中のClosed testingトラック。初期の旧トラックへ戻さない |

Google PlayのAAB欄へ登録するファイルは上表のAABだけとする。`app/release/`はGit除外対象であり、成果物、署名鍵および秘密値を本リポジトリへ追加していない。

## 3. 成果物と保管

以下を`app/release/1.0.0-9/`へ複製した。

| 種別 | ファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-9.aab` | 13,039,823 | `C619CA30F6747F408F56F15CA3120382DA384F5018D1DFBBD145C7130256E461` |
| R8 mapping | `mapping.txt` | 90,223,216 | `82BAA7E930F1B291697CCB950C48E64BBC15EAF0172E879922F511C11940EB68` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `E8BCD0C2479D767DE785A2F2804FAE00B43C1F8D7CE64577E60E6CA6624FB7F3` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `93A2160E56FE55476C643870BCEAD58C11FAA5E6C77C5E8169000F8225C77AF7` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,927 | `F1DECF3D58D950D76C831FA4F68D10CF87E0942BFF716E29EA297EB75973D570` |
| Releaseメタデータ | `release-metadata.json` | 1,857 | `63FC3C3DCE04D5AA6F31AD31E389D91020045FCECF917905B4D5556AC6286251` |
| Release事前検査 | `release-readiness.json` | 1,156 | `B5ABDA24FAF3F31DA666CB6BA162F28BC5D322B741337EB1AB91E1517CD2913B` |
| GitHubゲート検査 | `github-release-gates.json` | 3,413 | `2BE1DBD0B11BB70765F4FFABD6B041E25703AF57A726E57EF8A3C55DA82FBB05` |

5成果物は生成元メタデータと複製後の容量・SHA-256が一致した。3証跡も生成元と複製後の容量・SHA-256が一致した。GitHubゲートの`failed`は変更せず保管した。以前のversionCode `8`等の保管物は上書きしていない。

| ZIP項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/MATA-1.0.0-9-release-candidate.zip` |
| 容量 | `19,266,088 bytes` |
| SHA-256 | `EA30705F500F3866E1B21E4307966A89907A4C9E9FC1C983E12EBA6FE03214A1` |
| 収録ファイル数 | 8 |
| 内容検査 | 8ファイルすべてをZIP内から読み、保管元と容量・SHA-256が一致 |

ZIPは保管・受け渡し専用であり、Google Playへアップロードしない。

## 4. 自動検証と本番向けゲート

- [x] PR #301の[Android CI run 38498469622](https://github.com/Sakemotti/MATA/actions/runs/38498469622)が成功。単体試験、Debug Lint・ビルド、Release、Performance APK、API 30 instrumented test、Repository securityおよび最終集約が成功した。
- [x] PRの[CodeQL run 38498468301](https://github.com/Sakemotti/MATA/actions/runs/38498468301)が成功した。
- [x] 生成元mainの[Android CI run 38499723478](https://github.com/Sakemotti/MATA/actions/runs/38499723478)と[CodeQL run 38499723503](https://github.com/Sakemotti/MATA/actions/runs/38499723503)が成功した。
- [x] versionCode `9`のローカル`testDebugUnitTest lintDebug assembleDebug`がstrict依存関係検証で成功し、単体試験228件に失敗はなかった。
- [x] `:app:lintRelease :app:generateReleaseArtifactMetadata :app:verifyReleaseManifestSecurity :app:verifyUploadSigningGuards -PMATA_REQUIRE_UPLOAD_SIGNING=true --dependency-verification strict --no-configuration-cache --no-daemon`が成功。署名設定の異常系5件を拒否した。
- [x] `node tools/release/verify-readiness.mjs --release`の全7検査が合格。5成果物のハッシュ、226 runtime component、単一Upload Key署名者および期待する証明書SHA-256が一致した。
- [x] リリース検証ツールの回帰試験、試験実施区分424件、自動試験との1対1対応336件、アーキテクチャ検査および外部データ契約検査が合格した。
- [x] 候補生成時に既存の試験結果台帳でP0/P1 405/405件の合格を確認した。versionCode `9`のPlay経由更新・実機・テスター再確認の合格を意味しない。
- [ ] 候補生成時の`node tools/release/verify-github-release-gates.mjs`の総合結果は`failed`だった。失敗は`open_issues`の1件のみで、その時点では[Issue #299](https://github.com/Sakemotti/MATA/issues/299)を原報告者再確認待ちとして保持していた。2026-10-14の改善確認後も生成時のJSON・ZIPは書き換えず、現在の本番公開ゲートは別途再検査する。

GitHubゲートのmain一致、クリーンな作業ツリー、未解決PR 0件、ブランチ保護、必須チェック、セキュリティ機能、open alert各0件および生成元CIは合格した。CodeQL Java/Kotlin未対応だけを既知の警告として記録した。Issueを形式的に閉じる、保護を迂回する、ゲート結果を合格へ書き換える等の操作は行っていない。

## 5. 前回版との比較・署名診断

- versionCode `8`とのSBOM比較では、226 runtime component中、`kotlin-stdlib`と`kotlin-stdlib-common`だけが`2.4.20`から`2.4.21`へ変わり、他のcomponentのpurlに差分はなかった。依存関係グラフは227ノードで未解決参照は0件だった。
- オープンソースライセンス一覧は、Kotlinのバージョン表記を`2.4.20`から`2.4.21`へ置き換えると完全一致した。
- 最終Manifestは`android:versionCode`を`8`から`9`へ置き換えると完全一致し、新しい権限・コンポーネントはない。
- DBスキーマとバックアップ形式にソース差分はない。
- AABにBaseline Profileの`baseline.prof`（12,033 bytes）と`baseline.profm`（1,516 bytes）を含む。
- AABに4 ABI合計8件のネイティブライブラリを含み、取得可能なNative Debug Symbolsは0件だった。依存元でシンボル除去済みの既知制約は維持される。今回のPlay Console警告はアップロード後に確認する。

JBR 25.0.3の`jarsigner -verify`も終了コード0の`jar verified.`だった。自己署名・タイムスタンプなし等の診断に加え、JarFileとJarInputStreamの差異に関する警告を表示したため、既にGoogle Playで受理されたversionCode `8`の保管AABと比較した。両版とも1,761エントリで`META-INF/MANIFEST.MF`は最後にあり、同じ診断だった。

[OracleのJarInputStream仕様](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/JarInputStream.html)では、Manifestと署名関連エントリの先頭配置がストリーム署名検証の条件である。今回の配置による警告という推定は前版比較と仕様に基づき、Play Consoleの検証成功を先取りしない。Gradle側ではJarFileで各エントリを最後まで読み、署名検証と証明書の一致を確認した。AABの並べ替え・再署名・署名検証の無効化は行っていない。

## 6. 引き渡しと残りの確認

候補生成時点では未配布だった。その後2026-10-14に、原報告者本人がversionCode `9`をGalaxy S26／Android 16（報告時と同端末）で確認し、改善したとUSERが報告した（CT-021）。公開済み・本日公開・既存警告のみ、OWNERの8→9更新・データ保持に問題なしとのUSER報告も記録した（CT-022）。さらに同日にCT9-02〜CT9-07のOWNER回帰6件すべて問題なしとの報告を受け、[登録・更新確認手順](closed-testing-release-1.0.0-9.md)に結果を記録した（CT-023）。Production向け承認済みとは扱わない。

- Google Play公開状態は公開済み、公開日は2026-10-14、既存警告のみ・新規警告なしとUSER確認。公開時刻・解析結果の照合、SDK Index・ポリシー・App content・AdMobとPre-launch reportの今回の個別状態は未記録である。
- OWNERの`8`→`9`更新・データ保持は問題なしとUSER確認（CT9-01・CT-022）。CT9-02〜CT9-07の6件も2026-10-14のUSER報告ですべて合格・確認完了とした（CT-023）。今回のOWNER環境・実施日時・個別所見は未記録のまま維持する。
- 原報告者のGalaxy S26／Android 16、versionCode `9`での改善確認は2026-10-14に完了した。CT9-08へ報告範囲を記録し、再確認時の通知件数等の未報告の個別条件は推測で補完しない。
- Issue #299は原報告者の改善再確認により完了扱いとする。結果をClosed testing台帳へ記録し、ストア外の回答をGoogle Playへのフィードバック投稿済みとしない。
- Productionへ自動昇格せず、追加テスト、Production access再申請・承認および本番公開ゲートを別途完了させる。
