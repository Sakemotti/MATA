# MATA 1.0.0 (11) Closed testing候補生成結果

- 状態: 署名済み候補の生成・検証・保管済み。Google Playへのアップロード・公開、配布後回帰・原報告者再確認は未実施
- 実施日・最終更新日: 2026-10-26（日本時間）
- 実施者: AUTO
- 判定: Closed testing候補の技術的検査は合格。本番向けGitHubゲートは未解決PRにより未合格であり、Production公開承認ではない
- 登録手順: [MATA 1.0.0 (11) 登録・更新確認手順](closed-testing-release-1.0.0-11.md)

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `11` |
| ソースcommit | `13af30baeec2c78de4661beda98535877585029c` |
| ソースブランチ / 作業ツリー | 生成時は`main` / クリーン |
| ビルド日時（UTC） | `2026-10-25T23:28:54.851805400Z` |
| 署名方法 / 署名者数 | Upload Key / 1件 |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| `publishable` | `true`。署名条件の技術的判定であり、Production公開承認ではない |

[PR #321](https://github.com/Sakemotti/MATA/pull/321)の広告安全領域修正を含み、[PR #322](https://github.com/Sakemotti/MATA/pull/322)でversionCodeとリリースノートを更新した。両PRの全CI成功後に通常マージし、クリーンなmainから生成した。本書等の生成記録を追加する後続commitでAABの生成元を変更せず、生成済みAABを再生成・置換しない。

## 2. アップロード用AAB

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/1.0.0-11/mata-1.0.0-11.aab` |
| 容量 | `13,084,547 bytes` |
| SHA-256 | `3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE` |
| 配信先 | 現在運用中の新しいClosed testingトラック |
| Consoleアップロード・公開・警告 | 未実施・未確認。既知の警告だけになるとは補完しない |

Google Playへ登録するのは上記AABだけとする。保管用ZIPやmappingをAAB欄へアップロードしない。生成物はGit除外対象であり、署名鍵・パスワード・ローカル設定を含めない。

## 3. 成果物と保管

以下を`app/release/1.0.0-11/`へ複製し、容量・SHA-256を照合した。

| ファイル | bytes | SHA-256 |
| --- | ---: | --- |
| `mata-1.0.0-11.aab` | 13,084,547 | `3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE` |
| `mapping.txt` | 90,771,936 | `B0CA2D5BDE30084499F2846F270DE65B8B65C91D545188D5F46AF11EE9D10419` |
| `aboutlibraries.json` | 108,031 | `E8BCD0C2479D767DE785A2F2804FAE00B43C1F8D7CE64577E60E6CA6624FB7F3` |
| `AndroidManifest.xml` | 13,462 | `C51E4E482093A79F6C085C6B0C3E6DFFA07E4B5C23A2AD5AAA631EEE62430211` |
| `release-sbom.cdx.json` | 605,927 | `A622BAC5AF6CC284BFF5BC3BD2D5CDED3B7D6C7CFEBDC371144085A954E081A9` |
| `release-metadata.json` | 1,858 | `EECE07593C8E700E56598CDF7A2A365B8EA9AF0EA1E94DD544FAD4C7E573B51F` |
| `release-readiness.json` | 1,158 | `2D9D2A5DC81E7421F6A37AC5E605AD463C8D79FA142F09463EB09BC22A5A18AE` |
| `github-release-gates.json` | 3,413 | `52D57264D793903441BB7A6F044FB07BC56046BCC647B66D063F3596222E726B` |

| ZIP項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/MATA-1.0.0-11-release-candidate.zip` |
| 容量 | `19,345,392 bytes` |
| SHA-256 | `732752465ECAE64A7BA5B13CE977CB2D8C556D19B3E7129CAAE91D973BBB739C` |
| 収録ファイル数・内容検査 | 全8ファイルをZIP内から読み、保管元と容量・SHA-256が一致 |

既存のversionCode 10以前の保管物は上書きしていない。versionCode 10のAABのSHA-256は`2203C2F7934D36CAA63D98412DFB097E626661EB8BC56B056A53908F12EDCB99`のまま確認した。

## 4. 自動検証と本番向けゲート

- PR #322の[Android CI](https://github.com/Sakemotti/MATA/actions/runs/40244052818)と[CodeQL](https://github.com/Sakemotti/MATA/actions/runs/40244050673)は全成功。単体試験・Lint・Debug／Release／Performance APK・API 30 instrumented testを含む。
- 生成元mainの[Android CI](https://github.com/Sakemotti/MATA/actions/runs/40244929468)と[CodeQL](https://github.com/Sakemotti/MATA/actions/runs/40244929455)も成功した。
- 設定済みUpload Keyを用いた`:app:lintRelease :app:generateReleaseArtifactMetadata :app:verifyReleaseManifestSecurity :app:verifyUploadSigningGuards`に成功した。署名必須、strict依存関係検証、Configuration Cacheなしを維持し、秘密値は出力していない。
- `node tools/release/verify-readiness.mjs --release`は全7検査に合格。生成元commit、クリーンなmain、署名、5成果物のハッシュ、226 runtime component、法的文書、ストア掲載物を確認した。
- 署名設定の異常系5件に合格し、Release BuildConfigの本番広告App ID・バナーID・法的URL・versionCode 11が既存の正式設定と一致した。
- GitHubゲートは通常依存関係PR [#310](https://github.com/Sakemotti/MATA/pull/310)・[#311](https://github.com/Sakemotti/MATA/pull/311)の2件が未解決のため`failed`。Issueは0件、生成元CI・保護設定・セキュリティ機能・open alert各0件は合格した。CodeQL Java/Kotlin未対応の警告は維持する。結果を書き換えたり、形式的なクローズや保護回避で合格へ変更したりしない。

本番向け未合格とClosed testing候補の技術的合格を区別する。Issue #320の実装時の単体240件・UI41件とUSERによる4画面の表示確認は[修正確認記録](banner-system-insets-320.md)を参照し、配布後の実機確認や原報告者確認へ流用しない。

## 5. 前回版との比較・署名診断

- versionCode 10とのSBOM比較では、226 runtime componentのpurlに追加・更新・削除はなく、ライセンス一覧も同じ容量・SHA-256だった。
- 最終ManifestはversionCode 10→11以外に差分がない。権限・exported component等の追加はない。Roomスキーマ9・バックアップ形式6も変更しない。
- AABにBaseline Profileの`baseline.prof`（12,028 bytes）と`baseline.profm`（1,501 bytes）を含む。
- ネイティブライブラリは8件、取得可能なNative Debug Symbolsは0件。Play Consoleで実際に表示される警告は登録時に別途記録する。
- JBRの`jarsigner -verify`は終了コード0・`jar verified.`だった。自己署名、信頼チェーン、タイムスタンプなし、POSIX属性、JarFile／JarInputStreamの差異に関する診断があり、前版にも同種の記録がある。GradleのJarFile検証と単一署名者・期待証明書一致を確認済み。検証無効化・並べ替え・再署名は行わず、Play Consoleでの受理を先取りしない。

## 6. 引き渡しと残りの確認

現在の新トラックへのアップロード・公開、Console状態・日時・警告の記録、アンインストールせず10→11へ更新したデータ保持、広告を含む4画面の回帰確認、原報告者R3-T16の再確認は未実施。次の[登録・更新確認手順](closed-testing-release-1.0.0-11.md)で追跡する。

継続参加・実用とフィードバックを追跡し、Production accessと本番向けゲートは別途判定する。生成済みAAB・ZIP・生成時のゲートJSONを変更せず、Productionへ自動昇格しない。
