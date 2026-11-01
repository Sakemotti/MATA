# 初回リリース進行記録

- 対象: MATA初回公開（追加Closed testing版`1.0.0 (11)`は公開済み）
- 状態: Production access承認済み／本番候補は既登録の1.0.0 (11)に確定／最終公開判断・本番公開は未実施／全P0/P1 405件の既存結果は合格／versionCode 11のOWNER回帰はOK、R3-T16の公開保留はUSER判断で解除済み
- 最終更新日: 2026-11-01
- 親仕様: [リリース・配布運用仕様](README.md)
- 公開判定基準: [リリースチェックリスト](release-checklist.md)
- 試験状況: [初回リリース試験棚卸し](../../test-specs/initial-release-inventory.md)
- Closed testing記録: [初回Closed testing実施台帳](../../test-specs/closed-testing-log.md)
- 前回版のClosed testing引き渡し: [MATA 1.0.0 (8) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-8.md)
- 前回版のClosed testing候補証跡: [MATA 1.0.0 (8) 公開候補生成結果](../../test-specs/release-candidate-1.0.0-8.md)
- versionCode 9の引き渡し・証跡: [MATA 1.0.0 (9) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-9.md)、[候補生成結果](../../test-specs/release-candidate-1.0.0-9.md)
- versionCode 10の配布記録: [MATA 1.0.0 (10) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-10.md)、[候補生成結果](../../test-specs/release-candidate-1.0.0-10.md)。未完了TODO一覧と詳細画面等のレイアウト改善を含む。公開済み、OWNERのデータ保持・回帰確認は問題なし。2026-10-25に原報告者本人の追加機能再確認も問題なしとUSER報告（CT-027）
- versionCode 11の配布記録: [候補生成結果](../../test-specs/release-candidate-1.0.0-11.md)、[登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-11.md)。広告安全領域修正を含む。2026-10-27にUSERからアップロード・公開済み、既存の警告のみと報告（CT-028）。2026-10-29に10→11のデータ保持・OWNER回帰はOKと報告（CT-029）。条件別詳細は未記録。2026-11-01にR3-T16の再確認待ちはUSER判断で公開保留を解除し、新たな試験合格は追加しない
- 旧版のClosed testing引き渡し: [MATA 1.0.0 (7) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-7.md)
- 旧版のClosed testing候補証跡: [MATA 1.0.0 (7) 公開候補生成結果](../../test-specs/release-candidate-1.0.0-7.md)
- 旧本番公開候補計画: [MATA 1.0.0 (6) 本番公開候補生成計画](../../test-specs/production-release-candidate-1.0.0-6-plan.md)
- リリースブロッカー監査: [versionCode 6 リリースブロッカー監査票](version-6-blocker-audit.md)
- versionCode 6回帰範囲: [versionCode 5以降の差分棚卸し・versionCode 6回帰試験計画](../../test-specs/version-6-delta-and-regression-plan.md)
- versionCode 6実施記録: [回帰試験実施票](../../test-specs/version-6-regression-results.md)、[公開候補生成結果](../../test-specs/release-candidate-1.0.0-6.md)
- Production access申請: [申請回答・実施票](production-access-application-draft.md)
- Production公開: [初回Production公開実行票](initial-production-runbook.md)
- 現在の本番公開候補: [1.0.0 (11)の確定・昇格確認](../../test-specs/production-candidate-1.0.0-11.md)。既登録AABを再ビルドせず使用する
- 外部最終確認: [Console・AdMob・掲載情報の確認票](../../test-specs/production-console-final-check-1.0.0-11.md)

## 1. 記録方針

本書は初回リリース固有の進行状況を記録する。仕様書とリリースチェックリストは再利用する基準文書であり、その未選択チェックボックスを本書の完了状況で上書きしない。

2026-10-31にUSERが本番環境アクセス申請の承認を報告した。Production access待ちは解消したが、本番リリースの作成・審査・一般公開は未実施である。過去の不承認と申請準備の記録は履歴として保持し、現在の残作業は第7節を正とする。実際の申請・承認日時、正確なConsole文言・メール状況は未記録であり、報告日から補完しない。

同日にUSERが現在の最新を本番公開候補とすることを了承した。最新main `2a74058319b95325684afa34dd77cffeeac04a98`とversionCode 11生成元の差分にRelease入力の変更はなく、保管済み5成果物・3証跡ファイル・ZIPのハッシュと署名を再照合したため、既登録11を候補として固定する。選定時のGitHub監査では未解決の通常Dependabot PR5件により未合格であり、候補確定を公開可能との判断には置き換えない。

その後のUSER依頼に基づくDependabot整理は、5件を次回開発向けmainへ統合して検証する。本番候補11のAABは変更せず、更新後mainの依存構成・CIと候補生成元の実績を区別する。今回の依存更新を配布する場合は12以上の別候補と差分回帰を必要とし、11の再生成・上書きや、未完了の外部確認の合格化は行わない。

2026-11-01に[統合PR #332](https://github.com/Sakemotti/MATA/pull/332)を全CI成功後に通常マージした。元のDependabot PR #310・#311・#324・#325・#326はすべて`MERGED`で、確認時の未解決PRは0件となった。単体240件、strict再検証、Debug／ReleaseのLint・ビルド、API 30 instrumented test、SBOM・Manifest検査とCodeQLは合格。本番候補11の保管物は不変であり、[統合検証記録](../../test-specs/dependabot-maintenance-2026-11-01.md)へ実績を記録した。公開直前のGitHubゲート再照合と外部最終確認は別に行う。

同日の最終確認開始時main `521a70457bac9103d8df670291d409eb3f9ac237`でGitHubゲートは合格した。USER判断でR3-T16の公開保留を解除し、Consoleの製品版ページ・本人確認／連絡先・App content・新規ポリシー／SDK警告なし・日本のみ／無料／商品なしと、画像以外の掲載情報は問題なしと回答を受領した。最新画像への差し替えは未完了、Pre-launch reportは表示なしで結果未確認。公開Web9ファイルは両UAで正本一致・取得成功、公開リポジトリ13ファイルも正本一致で再同期不要だった。AdMobは新規警告・配信制限なし、UMP公開済み、ストア連携は正式版URL待ち。「必須ではありません」の表示から個別の準備状況やアプリ承認完了を推測せず、一般公開後へ確認を引き継ぐ。詳しい証跡・残確認は外部最終確認票を参照する。

確認元を次のように区別する。

- `AUTO`: リポジトリまたはCIによる機械検査
- `CONSOLE`: Google Play ConsoleまたはAdMobの表示
- `DEVICE`: Google Playから取得したReleaseの実機確認
- `WEB`: 認証なしの公開URL確認
- `USER`: ユーザーが外部サービス上の操作結果を確認

## 2. リリース識別と成果物

| 項目 | 確定値・結果 | 確認元 |
| --- | --- | --- |
| Application ID | `com.mochisofts.mata` | AUTO |
| versionName | `1.0.0` | AUTO |
| versionCode | `11`。2026-10-26に生成・署名・検証・保管済み。2026-10-27にUSERから現在のClosed testingへ公開済み、既存の警告のみと報告（CT-028）。実際の公開日時・正確なConsole状態文言は未記録。2026-10-29に10→11のデータ保持・OWNER回帰はOKと報告（CT-029）。条件別詳細は未記録。R3-T16の公開保留は2026-11-01 USER判断で解除 | AUTO / USER |
| ソースcommit | `13af30baeec2c78de4661beda98535877585029c`（versionCode 11保管候補の生成元） | AUTO |
| AAB | `app/release/1.0.0-11/mata-1.0.0-11.aab`。Git除外対象 | AUTO |
| AAB容量 | 13,084,547 bytes | AUTO |
| AAB SHA-256 | `3d2f763f6a33a87f3910835a0fb86764e1208a1970ebba88bcfb89737d9f55ee` | AUTO |
| ビルド日時（UTC） | `2026-10-25T23:28:54.851805400Z` | AUTO |
| 署名方法 | Upload Key、署名者1件、`publishable=true` | AUTO |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` | AUTO / CONSOLE |

追加Closed testing版`1.0.0 (8)`は、Issue #281のウィジェットカード化・固定プレビューとIssue #284の土日祝日色分けを含む。2026年10月8日に公開し、既知のネイティブデバッグシンボル未登録警告のみだった。更新・データ設定保持、ウィジェット・日付色／テーマ別表示はOKであり、T01も同日にPixel 9a／Android 16で改善を再確認してOKと報告した。2026年10月9日に詳細条件の確認完了、T01が開発者とは別のテスターであること、再申請可能・12人以上／連続14日以上とConsole・AdMobに新規警告・未完了なしを確認した。個別所見と公開時刻等は推測で補完しない。

本番公開候補versionCode `6`は、mainのcommit `4bdb50bbef2f3ed15ebcb7e0a69199d53e5e6757`から2026年9月23日に生成した。AABは`app/release/1.0.0-6/mata-1.0.0-6.aab`へローカル複製し、容量12,905,674 bytes、SHA-256 `59133b8f7707dc960e0808436c0c8e13faf12e06e71da7fd8fed0f6c55cde9a4`、Upload Key署名者1件、`publishable=true`を確認した。自動試験10/10件は合格し、実機回帰18件とGoogle Play登録後確認7件は未実施である。

追加Closed testing候補versionCode `7`は、mainのcommit `6cf969d8a13f66fc030267c784116e65c19b9da8`から2026年10月6日に生成した。AABは`app/release/1.0.0-7/mata-1.0.0-7.aab`へローカル複製し、容量13,033,358 bytes、SHA-256 `f0ec8832d469891abef15ea59e6743915cae3d011dd5dc36bef009efba7259bb`、Upload Key署名者1件、`publishable=true`を確認した。同日にClosed testingで公開完了となり、Console警告は`ネイティブコードを含むがデバッグシンボル未登録`だけだった。versionCode `6`はGoogle Playへ未登録で、その後にIssue #274と依存関係更新が入ったため配布しない。

versionCode `1`はInternal testing、versionCode `2`、`3`、`4`、`5`、`7`、`8`、`9`、`10`および`11`は登録済みであり、再アップロードできない。versionCode `9`を2026-10-14に公開済み、既存警告のみ・新規警告なしとUSERが報告した。公開時刻は未記録である。versionCode `5`と未登録のversionCode `6`はProductionへ昇格せず、現行版のClosed testing検証とProduction access承認後に本番昇格可否を再判定する。

2026年10月14日に、Issue #299を修正したClosed testing候補versionCode `9`をmainのcommit `53bb2764e935c13430e3360460032b6dd48c9007`から生成・署名・検証・保管した。AABは[versionCode 9候補生成結果](../../test-specs/release-candidate-1.0.0-9.md)のとおり既存Upload Keyと一致し、生成元CIとRelease事前検査7件は合格した。生成時点ではIssue #299の原報告者再確認待ちにより本番向けGitHubゲートは`failed`だった。このJSON・ZIPとAABは歴史証跡として変更しない。

その後、2026-10-14に原報告者本人がversionCode `9`をGalaxy S26／Android 16（報告時と同端末）で確認し、改善したとUSERが報告した（CT-021）。Issue #299は修正・原報告者再確認完了とする。同日に公開済み・本日公開・既存警告のみ、OWNERの`8`→`9`更新とデータ保持は問題なしとUSER報告（CT-022）。続いてCT9-02〜CT9-07の回帰6件すべて問題なしとの報告を受け、今回のversionCode 9回帰確認を完了とした（CT-023）。公開時刻とOWNERの端末・OS・実施日時・個別所見は未記録であり、全試験の再実施や本番公開承認へ広げない。個別参加者の対応表はリポジトリ外で管理する。

## 3. リポジトリと自動検査

2026-10-29にUSERから10→11のデータ保持・OWNER回帰確認はOK、R3-T16による広告位置の再確認は回答なしと報告された（CT-029）。端末・OS・実施日時・条件別詳細は未記録。警告は既知のみ、Pre-launch reportは何も表示されておらず結果未確認である。また、Play非公開フィードバックは4件（参加者との対応不明1件）へ訂正され、その1件のカテゴリ別管理への肯定的な感想をFB-011として台帳と申請原稿へ反映した。4件に含まれる投稿の内容補足であり、5件目や別のテスターとして加算しない。次回申請予定は2026-10-30で、要件達成・申請送信・Production承認は未確認のまま維持する。

2026-10-27にUSERからversionCode `11`を現在のClosed testingへアップロード・公開済み、警告は既存のもののみと報告を受領した（CT-028）。実際の操作・公開日時、正確なConsole状態文言と警告の文言・件数は未記録。公開完了と10→11更新・回帰・原報告者再確認は分けて扱い、後者は未実施のまま維持する。生成時のAAB・ZIP・ゲートJSONは変更しない。

2026-10-26に[PR #322](https://github.com/Sakemotti/MATA/pull/322)を全CI成功後に通常マージし、生成元mainのAndroid CI・CodeQLも成功した。Issue #320の修正を含むversionCode `11`をクリーンなmainから生成し、Release事前検査7件、単一署名者・期待証明書、5成果物のハッシュ、8保管ファイルとZIP内容を照合した。前版とSDK・ライセンス・権限・DBスキーマ・バックアップ形式の差分はない。本番向けGitHubゲートは通常依存関係PR #310・#311が未解決のため`failed`のまま保管した。詳細は[候補生成結果](../../test-specs/release-candidate-1.0.0-11.md)を参照し、公開・配布後確認の完了を補完しない。

2026-10-19にUSERからversionCode `10`のアップロード済み・既知の警告のみとの報告を受領した（CT-024）。実際のアップロード日時と警告の正確な文言・件数は未記録である。審査送信・公開完了、9→10の更新・データ保持、テスター再確認は今回の報告から補完しない。

続いて同日にUSERからversionCode `10`の公開済み（CT-025）、データ保持・回帰確認は問題なし（CT-026）との報告を受領した。CT10-01〜CT10-10をOWNERの一括報告として合格とし、テスターによる追加機能再確認（CT10-11）はその時点では未確認とした。正確なConsole状態文言、実際の公開・確認日時、端末・OS・条件別所見は未記録であり、報告日や前版の環境を転用しない。既存の全P0/P1の再実施やProduction access承認は意味しない。

2026-10-25にUSERから、R3-T11（原報告者本人）が同日`1.0.0-10`で未完了TODO一覧の追加機能を確認して問題なしと報告を受領した（CT10-11・CT-027・FB-009）。端末・Androidは不明、具体的な報告経路・操作別所見は未記録。原報告者の配布後再確認を完了とし、Google Playへの新しいFB投稿、Issue #306の発生条件特定、全試験再実施やProduction access承認とは分ける。

2026年10月19日に[PR #313](https://github.com/Sakemotti/MATA/pull/313)を全CI成功後に通常マージし、生成元mainのAndroid CI・CodeQLも成功した。versionCode `10`を生成し、Release事前検査7件、署名・5成果物ハッシュ、8保管ファイルとZIP内容を照合した。SDK・ライセンス・権限等に前版から追加はないが、Room8→9・バックアップ形式5→6への移行を含む。生成時の本番向けGitHubゲートはIssue #306と通常依存関係PR #310・#311により`failed`のまま保管した。詳細は[候補生成結果](../../test-specs/release-candidate-1.0.0-10.md)に記録し、配布後の実機確認や本番承認へ広げない。

2026年10月8日にversionCode `8`を設定した[PR #287](https://github.com/Sakemotti/MATA/pull/287)を全CI成功後にマージした。PRの[Android CI run 37712733228](https://github.com/Sakemotti/MATA/actions/runs/37712733228)はAPI 30 instrumented testを含む全ジョブに成功し、生成元mainの[Android CI run 37713981531](https://github.com/Sakemotti/MATA/actions/runs/37713981531)と[CodeQL run 37713981879](https://github.com/Sakemotti/MATA/actions/runs/37713981879)も成功した。クリーンなmainから署名済み候補を生成し、Release事前検査7/7件、署名・5成果物ハッシュ・GitHubゲート・8保管ファイルとZIP内容の照合に合格した。versionCode `7`と比較してSDK依存関係とライセンスは同一で、ManifestはversionCode以外に変更がない。詳細は[versionCode 8公開候補生成結果](../../test-specs/release-candidate-1.0.0-8.md)に記録する。

2026年9月17日にversionCode `6`生成前の初回ブロッカー監査を行った。mainのcommit`3adc1623c9b2e6d890c7331b7ed118aca14719bd`はクリーンで`origin/main`と一致し、未解決Issue・Pull Requestは各0件、対象commitの[Android CI run 35235533436](https://github.com/Sakemotti/MATA/actions/runs/35235533436)と直近5件のmain CIは成功していた。Git管理下と作業ディレクトリに署名鍵・service credentialはなく、CI対象の秘密情報パターンにも該当はなかった。一方、GitHub標準のDependabot alerts、vulnerability alerts、code scanningおよびsecret scanningは無効または解析実績がなく、警告件数は判定不可である。既存CIのRepository security checksと週次Dependabot更新を維持し、候補生成直前に[監査票](version-6-blocker-audit.md)を再実行する。

2026年9月18日にGitHub標準セキュリティ機能を強化した。Dependabot alerts、security updates、secret scanningおよびpush protectionを有効化し、初回取得時のopen alertはDependabot 0件、secret scanning 0件だった。CodeQL default setupではGitHub ActionsとJavaScript/TypeScriptの初回解析が成功し、open alertは0件だった。Java/Kotlin解析はGitHubが提供するCodeQL 2.27.0がKotlin 2.4.20未対応のため失敗し、GitHubの自動調整で対象から除外された。通常のAndroid CIは影響を受けておらず、この制約は[監査票](version-6-blocker-audit.md)に記録して候補生成直前に再確認する。

同日、mainのcommit `0518c88483656b1406e9426ee8a006e5372b4549`でGitHubリリースゲート自動監査を実行した。作業ツリーとリモートの一致、未解決Issue・Pull Request各0件、branch protection、必須チェック、GitHubセキュリティ機能、open alert各0件、[Android CI run 35300355328](https://github.com/Sakemotti/MATA/actions/runs/35300355328)および[CodeQL run 35300355344](https://github.com/Sakemotti/MATA/actions/runs/35300355344)の成功を確認した。Java/KotlinのCodeQL未対応だけを既知制約として残し、候補生成直前に同じ監査を再実行する。

同日に`main`のbranch protectionを有効化した。Pull Request、最新baseへの追従、`Test, lint, and build`と`CodeQL`の成功および会話解決を必須とし、管理者にも適用した。承認レビュー数は単独開発を継続できるよう0とし、force pushとbranch削除は禁止した。

2026年9月23日にversionCode `6`を設定した[PR #266](https://github.com/Sakemotti/MATA/pull/266)をマージした。PRの[Android CI run 35815319699](https://github.com/Sakemotti/MATA/actions/runs/35815319699)はAPI 30 instrumented testを含む全ジョブに成功し、mainの[Android CI run 35816296254](https://github.com/Sakemotti/MATA/actions/runs/35816296254)と[CodeQL run 35816296273](https://github.com/Sakemotti/MATA/actions/runs/35816296273)も成功した。クリーンなmainからUpload Key署名済みAABと5成果物を生成し、`node tools/release/verify-readiness.mjs --release`の全7検査、P0/P1 405/405件、自動試験10/10件およびローカル複製後の容量・SHA-256再照合に合格した。

2026年10月6日にversionCode `7`を設定した[PR #278](https://github.com/Sakemotti/MATA/pull/278)を全CI合格後にマージした。PRの[Android CI run 37316570956](https://github.com/Sakemotti/MATA/actions/runs/37316570956)はAPI 30 instrumented testを含む全ジョブに成功し、mainの[Android CI run 37318481519](https://github.com/Sakemotti/MATA/actions/runs/37318481519)も成功した。クリーンなmainからUpload Key署名済みAABと5成果物を生成し、`node tools/release/verify-readiness.mjs --release`の全7検査、226 runtime component、単一署名者およびローカル複製後の容量・SHA-256再照合に合格した。

2026年9月16日に`REL-010`の最終集計を行い、自身を除くP0/P1 404件の全合格、P2不合格・保留・対象外0件、GitHubの未解決Issue 0件を確認した。これによりP0/P1は405/405件合格となり、総合動作確認の試験ゲートは合格した。同日時点で開いていたDependabot通常更新PR 5件は、2026年9月16日に全件CI成功後マージ済みである。本番公開はClosed testing期間、Production access、Pre-launch reportおよび最終Console確認が完了するまで保留する。

2026年9月17日に、Closed testingへ配布したversionCode `5`の基準commit`09cbbc85d9c8ecb9db3137cbd62e03c22b8dcb0d`からmainのcommit`32aa301d5beff208083a3c64ec3752a89b60903f`までを棚卸しした。136 commits、89 filesの差分に本番コード31ファイルとCompose、Navigation、Room、Kotlin serializationおよびBenchmarkの更新が含まれるため、versionCode `6`では全自動ゲートに加え、TODO編集、カテゴリ、アーカイブ、終了時刻設定、バックアップ、通知・ウィジェット、広告および全画面スモークの差分回帰を必須とした。DBスキーマ、Manifest、バックアップアーカイブ形式および`app/build.gradle`には差分がない。詳細は[差分棚卸し・回帰試験計画](../../test-specs/version-6-delta-and-regression-plan.md)に記録する。

2026年9月13日にversionCode `5`について次を確認した。

- `main`のcommit`09cbbc85d9c8ecb9db3137cbd62e03c22b8dcb0d`から、更新したストア画像を含むクリーンな作業ツリーで署名済み成果物を再生成した。
- `node tools/release/verify-readiness.mjs --release`は、Git、法的文書、Play掲載成果物、5成果物のハッシュ、226 runtime componentおよびUpload Key署名を含む全7検査に成功した。
- [PR #194のCI run 34741198020](https://github.com/Sakemotti/MATA/actions/runs/34741198020)、[PR #195のCI run 34741651049](https://github.com/Sakemotti/MATA/actions/runs/34741651049)および[PR #197のCI run 34743486675](https://github.com/Sakemotti/MATA/actions/runs/34743486675)は、Repository security、Debug、Release、Performance APK、API 30 instrumented testおよび最終集約に成功した。
- Release AABの署名証明書SHA-256はPlay Consoleへ登録済みのUpload Keyと一致し、`publishable=true`である。

2026年9月11日にversionCode `4`について次を確認した。

- `main`のcommit`fe71e1579969d647e95170e47f2d036b19e5b657`から、クリーンな作業ツリーで署名済み成果物を生成した。
- `node tools/release/verify-readiness.mjs --release`は、Git、法的文書、Play掲載成果物、5成果物のハッシュ、226 runtime componentおよびUpload Key署名を含む全7検査に成功した。
- [PR #183のCI run 34587951161](https://github.com/Sakemotti/MATA/actions/runs/34587951161)は、Repository security、Debug、Release、Performance APK、API 30 instrumented testおよび最終集約に成功した。
- Release AABの署名証明書SHA-256はPlay Consoleへ登録済みのUpload Keyと一致し、`publishable=true`である。

2026年9月9日にversionCode `3`について次を確認した。

- `main`のcommit`307949e2068b1c56ff597c3730b05755b1f37e06`から、クリーンな作業ツリーで署名済み成果物を生成した。
- `node tools/release/verify-readiness.mjs --release`は、Git、法的文書、Play掲載成果物、5成果物のハッシュ、226 runtime componentおよびUpload Key署名を含む全検査に成功した。
- [CI run 34299331024](https://github.com/Sakemotti/MATA/actions/runs/34299331024)は、Repository security、Debug、Release、Performance APKおよびAPI 30 instrumented testを含む全ジョブに成功した。
- Release AABには4 ABI合計8件のネイティブライブラリがある。`SYMBOL_TABLE`を有効化したが、依存元ですでにシンボルが除去されているため取得可能なNative Debug Symbolsは0件だった。架空または空のシンボルファイルは作成せず、Play Consoleの警告を候補登録後に再確認する。
- 未解決のGitHub IssueおよびPull Requestは0件だった。
- PR #126のCIではAPI 30エミュレータ上のinstrumented test 54件が成功し、単体テスト、Lint、Debug・Release・Benchmark成果物生成とリリース成果物検査も成功した。
- Release成果物メタデータはAAB、R8 mapping、ライセンス、最終ManifestおよびCycloneDX SBOMの存在、容量、SHA-256を記録し、Upload Key証明書と一致した。
- リリース進行状況だけを記録する文書変更は、既にGoogle Playへ登録したAABを無効化しない。アプリ実装、ビルド設定、掲載成果物または法的本文を変更した場合は、versionCodeを上げた最終公開候補を新しいcommitから生成して全ゲートを再実行する。

## 4. Google Play・Web・実機で確認済み

| 項目 | 状態 | 確認元 |
| --- | --- | --- |
| 個人デベロッパー本人確認 | 完了 | CONSOLE / USER |
| 連絡先メール・電話番号 | 確認完了 | CONSOLE / USER |
| Android実機確認 | 完了 | CONSOLE / USER |
| Play ConsoleのMATAアプリ作成 | 完了 | CONSOLE / USER |
| App content | 未完了カードなし | CONSOLE / USER |
| ストア掲載文・画像・連絡先・Webサイト | 2026-11-01にUSERが画像以外は問題なしと回答。2026-10-27取得の最新画像14枚へのConsole差し替えは未完了。[更新記録](../../test-specs/store-screenshot-refresh-v11.md)を参照 | CONSOLE / USER / AUTO |
| 初回本番公開用文章 | 短い説明・詳細な説明は正本と`fastlane/metadata`へ確定済み、2026-11-01にUSERが掲載情報は問題なしと回答。掲載文仕様第4.2節の初回Production用リリースノートは11の登録時に照合する。6は当初計画の見出しで、Closed testingの`11.txt`を代用しない | AUTO / USER |
| Internal testing | `1.0.0 (1)`を公開し、参加URL経由でインストール済み | CONSOLE / DEVICE |
| `1.0.0 (2)`Closed testing候補 | Closed testingへ公開し、versionCode 1からの上書き更新と主要実機試験に使用済み | AUTO / CONSOLE / DEVICE / USER |
| `1.0.0 (3)`Closed testing候補 | 署名済みAAB生成とRelease事前検査に成功。Closed testingへ公開済み | AUTO / CONSOLE / USER |
| versionCode `2`→`3`上書き更新 | Pixel 9a（Android 17 / API 37）で成功。既存データ、設定、通知、ウィジェットおよびバックアップに問題なし | DEVICE / USER |
| `1.0.0 (4)`Closed testing候補 | 署名済みAAB生成とRelease事前検査に成功。Closed testingへ登録後、versionCode 5へ更新済み | AUTO / CONSOLE / USER |
| `1.0.0 (5)`Closed testing候補 | 署名済みAAB生成とRelease事前検査に成功。Closed testingへ公開済み。Productionへは昇格しない | AUTO / CONSOLE / USER |
| `1.0.0 (6)`本番公開候補 | commit `4bdb50b`からUpload Key署名済みAABを生成し、自動試験10/10件とRelease準備検査に合格。実機・Play登録後確認待ち | AUTO |
| `1.0.0 (7)`追加Closed testing版 | commit `6cf969d`からUpload Key署名済みAABを生成し、全CIとRelease準備検査に合格。2026-10-06に公開し、2026-10-07にversionCode 5からの上書き更新、データ保持およびテスターによる背景色設定の再確認に合格 | AUTO / CONSOLE / DEVICE / USER |
| `1.0.0 (8)`追加Closed testing版 | commit `82f998d`から生成・検証・保管済み。2026-10-08に公開、更新・改善はOK、別テスターT01がPixel 9a／Android 16で再確認してOK。2026-10-09に詳細条件も確認済み、新規警告・未完了なし | AUTO / DEVICE / USER |
| Play App Signing | 有効。Upload Key証明書が本書の値と一致 | CONSOLE |
| Upload Keyバックアップ | keystoreと復旧情報を暗号化された安全な別保管先へ保存済み | USER |
| 新規インストールと起動 | 問題なし | DEVICE |
| TODO等の主要機能 | 現状問題なし | DEVICE / USER |
| 通知・ウィジェット | 現状問題なし | DEVICE / USER |
| 手動バックアップ・復元 | 現状問題なし | DEVICE / USER |
| アプリ設定から法的ページへの遷移 | 確認済み | DEVICE / USER |
| AdMobとGoogle Playのアプリ連携 | 以前の確認済み記録は履歴として保持。2026-11-01のUSER回答では正式版URLが必要なため未完了であり、一般公開後に行う | CONSOLE / USER |
| 実広告バナー表示 | 確認済み。本番広告のクリックは実施していない | DEVICE / USER |
| Closed testing参加要件 | 2026年9月9日開始。2026年9月23日に12人以上・14日間連続の要件達成とProduction access申請可能状態を確認 | CONSOLE / USER |
| Production access | 承認済み（2026-10-31 USER報告）。第3回申請の実際の送信・承認日時と正確なConsole文言は未記録。本番リリースは未実施 | USER |
| ポリシーのステータス | `問題は見つかりませんでした` | CONSOLE / USER |
| versionCode `2`のSDK関連警告 | なし | CONSOLE / USER |

2026年9月4日に、次のURLが認証なしでHTTP 200を返すことを確認した。

- `https://mochisofts.com/`
- `https://mochisofts.com/mata/privacy`
- `https://mochisofts.com/mata/terms`
- `https://mochisofts.com/mata/external-transmission`
- `https://mochisofts.com/app-ads.txt`

`app-ads.txt`は`text/plain`で配信され、Publisher ID `pub-6387608801909086`を含む正式な1行と一致した。

### 4.1 法的サイト同期

2026年9月7日に、正本側commit`45ead6a73ac742899cb812e93fbbb0c01f1cf68f`と公開側commit`ad26db4aed4f97eaed7d74d66bcc44d63d90fda2`を対応付けた。公開側の[Pages deployment run 33757973477](https://github.com/Sakemotti/matadoc/actions/runs/33757973477)は成功している。

正本13ファイルと公開リポジトリmainの対応する13ファイルはGit blob SHAがすべて一致した。2026年9月7日06:50 JSTにモバイルUser-Agentでプライバシーポリシー、利用規約、外部送信公表および`app-ads.txt`を再取得し、全件HTTP 200かつ正本と同一であることを確認した。アプリ設定画面からのプライバシーポリシー・利用規約遷移は実機確認済みである。詳細は[法的サイト同期結果](../../test-specs/legal-site-sync-results.md)に記録する。

2026年9月17日に本番公開前の再確認を行い、公開側mainが引き続き`ad26db4aed4f97eaed7d74d66bcc44d63d90fda2`、正本13ファイルとのblob一致が13/13であることを確認した。Mobile・Desktop User-Agentでトップ、法的4文書、`app-ads.txt`、CSS、robotsおよびsitemapの9公開物を取得し、全件HTTP 200、適切なContent-Type、正本とのSHA-256一致を確認した。HTTPからHTTPSへの301転送、Pagesの状態`built`、カスタムドメイン検証、HTTPS強制および証明書承認も確認済みであり、再同期は不要と判断した。

## 5. Closed testingと本番アクセス

- 2026年9月7日にversionName `1.0.0`、versionCode `2`のAABをClosed testingへ公開した。
- Console上のエラーは0件で、Native Debug Symbols未登録の警告だけが表示された。依存ライブラリで取得可能なシンボルがないことを確認済みのため、警告を記録して公開を継続した。
- オプトインURLを取得できることを確認した。URLおよびテスター情報はリポジトリへ記録せず、制限された連絡経路で管理する。
- 2026年9月8日にPixel 9a（Android 17 / API 37）で、既存versionCode `1`からGoogle Play経由でversionCode `2`へアンインストールせず更新できることを確認した。TODO、カテゴリ、履歴、設定、通知およびウィジェットは維持され、基準バックアップ`B0`を作成した。
- 2026年9月8日に同端末で`RO-17`を実施し、実通知4件とウィジェット13件を合格とした。`WGT-014`は実広告の配置を確認し、2026年9月9日にversionCode `3`を機内モードで起動して広告非表示時も強制終了せず操作を継続できることを確認したため合格とした。
- 2026年9月9日にClosed testingフィードバックを反映したversionName `1.0.0`、versionCode `3`のAABを同じClosed testingトラックへ公開し、状態が`クローズドテスト公開開始`であることをUSERが確認した。新規警告はなかった。
- 同日にPixel 9a（Android 17 / API 37）でversionCode `2`から`3`へアンインストールせず更新し、既存データ、設定、通知、ウィジェット、バックアップおよび起動に問題がないことを確認した。
- versionCode 3変更内容の個別再試験はPixel 9a（Android 17 / API 37）で完了した。不足環境の確認は[残実機・環境試験計画](../../test-specs/release-v3-device-verification-plan.md)に従って実施する。
- versionCode `3`公開後もPre-launch report、SDK Indexおよびポリシー状態はversionCode `2`確認時から変わっていない。
- versionCode `4`はClosed testingへ登録後、versionCode `5`に更新された。versionCode `4`単独の正確な公開日時と警告は未記録である。
- versionCode `5`は主要画面のカードレイアウト改善を含むUpload Key署名済み候補をClosed testingへ公開済みである。公開済み状態は2026年9月17日にUSERが確認した。正確な公開日時、警告および上書き更新結果は未確認である。
- Closed testingの連続参加期間は2026年9月9日に開始し、2026年9月23日に12人以上・14日間連続の要件達成とProduction access申請可能状態を確認した。
- 2026年9月23日にProduction accessを申請し、2026年9月25日に不承認を確認した。理由は、テスターの関与およびフィードバックを更新へ反映した実績が不十分と判断されたことである。
- 2026年9月29日にT13からチャットで背景色設定の改善要望を受け、Issue #274へ登録した。固定色パレット、ライトテーマ限定表示、永続化およびバックアップ互換性を実装・試験し、versionCode `7`以上のClosed testing更新版でテスターの再確認を受ける。
- 2026年10月7日にversionCode `5`から`7`への上書き更新とデータ保持に問題がないことを確認した。同日にフィードバック報告者と同じT13が背景色設定を再確認し、問題なしと報告した。端末・OSおよび追加意見は未記録である。
- 2026年10月8日にversionCode `8`を既存Closed testingトラックへ公開完了し、警告は既知のネイティブデバッグシンボル未登録のみだったことをUSERが確認した。`7`→`8`更新・データ設定保持、ウィジェット表示・追加・完了／スキップ、土日祝日色分け・テーマ別表示の報告範囲はすべてOKであり、T01もPixel 9a／Android 16で配布後の改善を再確認してOKと報告した。
- テスターには試験項目を割り当てず、実際に複数日にわたって自由操作してもらい、利用状況と自由記述のフィードバックを収集する。
- 再申請まではClosed testingの参加設定と公開状態を維持し、フィードバック、Issue、更新版および再確認結果を関連付けて記録する。

## 6. 未完了・保留

### 6.1 Closed testing中に完了する

- テスターの端末・OS・利用期間・自由操作のフィードバックの記録
- Google PlayのSDK Index、権限申告およびポリシー警告の最終確認
- 利用規約と外部送信に関する公表について必要な専門家確認
- Data safety、UMP、SDK、実通信および公開法的文書の最終突合

必要人数と継続期間は2026年9月23日に達成した。初回・1回目再申請の不承認後に追加Closed testingを実施し、2026-10-31にProduction access承認をUSER確認した。参加人数や個別利用の未確認事項を補完せず、公開前の最終確認は別に行う。

### 6.2 次の実変更で確認する

- versionCode `5`では全自動ゲート、Upload Key署名および成果物ハッシュ検査に合格し、Closed testingへ公開済みである。Google Play経由の上書き更新、データ保持、カードレイアウトおよび読み取り専用詳細画面の結果は未確認のため、確認後に記録する。
- versionCode `5`はClosed testing検証版として固定し、Productionへ昇格しない。
- versionCode `5`以降の差分棚卸しとversionCode `6`向けに定義した回帰範囲は、versionCode `7`の基礎回帰範囲として引き継ぐ。Issue #274の背景色設定とバックアップ互換性を追加して確認する。
- versionCode `6`向けに準備した更新前テストデータ、実施セッションおよび結果欄は、未配布のversionCode `6`ではなくversionCode `7`の更新確認へ読み替えて使用する。
- versionCode `7`の署名済みAABと自動証跡はクリーンなmainから生成済みである。[versionCode 7登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-7.md)に従ったversionCode `5`からの上書き更新、データ保持およびテスターによる背景色設定の再確認は合格した。
- versionCode `6`はGoogle Playへ未登録のまま、その後にAABへ影響するIssue #274と依存関係更新が入ったため配布しない。現在のmainからversionCode `7`を生成済みである。
- Issue #274の背景色設定をversionCode `7`へ含め、2026年10月6日にClosed testingへ配布した。2026年10月7日にClosed testing参加者が再確認し、フィードバック対応を完了した。
- Issue #281・#284はversionCode `8`へ反映し、USERの更新・ウィジェット・日付色／テーマ別表示とT01の配布後再確認が合格した。2026-10-09に詳細条件（Dynamic Color・最大フォント、祝日更新／取得失敗、通知・バックアップ等）も確認済みとUSER報告。T01は開発者とは別のテスターだと確認した。

### 6.3 外部状態待ち

- 2026-10-25にR3-T16からIssue #320として、1.0.0-10のTODO一覧で広告がシステムナビゲーションに重なるとの報告を受領した（FB-010）。端末・Androidは不明、提供画像で3ボタンとの重なりを確認した。共通広告部品を修正し、UI41件・単体240件・Lint・Debugビルドに合格し、同日にUSERが4掲載画面で改善を確認した。PR #321を全CI成功後に通常マージし、2026-10-26に修正版のversionCode 11候補を生成・検証・保管した。2026-10-27に現在のClosed testingへ公開済み・既存の警告のみとUSER報告（CT-028）。2026-10-29に10→11のデータ保持・OWNER回帰はOKと報告（CT-029）。2026-11-01にUSERが再確認待ちは「これはもう大丈夫」と回答し、公開保留を解除した。条件別詳細と原報告者の確認日・端末等は未記録で、新たな試験合格や本番公開承認を補完しない。詳細は[確認項目](../../test-specs/banner-system-insets-320.md)と[versionCode 11手順](../../test-specs/closed-testing-release-1.0.0-11.md)へ記録する。
- Production accessは初回・1回目再申請の不承認を経て、2026-10-31に承認済みとUSER報告。2026-11-01に製品版ページを開けることもUSER確認。本番リリース作成・審査・公開は未実施である。
- 2026-10-09に再申請前の人数・期間・警告なしを確認し、原稿を確定した。その後2026-10-11に1回目の再申請も初回と同じ2理由で不承認だったとUSER報告。正確な申請・通知日時は未記録であり、[申請実行票](production-access-application-draft.md#9-今回の再申請実行票)へ過去履歴として記録した。
- 次回向けに[テスター依頼文](../../test-specs/closed-testing-tester-guide.md#12-配布メッセージひな型)と[14日間利用・確認計画](../../test-specs/closed-testing-14-day-plan.md)を準備した。2026-10-15にUSERが15人で開始したと報告し、運用開始日も同日と確認した。リポジトリ外の対応表でも開始報告15人を確認した。新トラックのConsole人数・継続日数は旧トラック表示のため不明であり、旧トラックの数値を流用しない。終了・要件達成予定は未確定。全員の毎日利用・投稿完了や個別版を補完せず、必要な更新配布と再確認は実績に基づいて追跡する。
- 2026-10-20のUSER手動更新後の対応表は15人分で、開始報告の受領日あり15人、Google Play非公開FB実施済の記録2人・未確認13人だった。こちらからのFB送信日あり9アプリは別の実績として管理する。前回の19人という記録は当時のものとして保持する。同日の追加説明で、現表から外れた4人はテスト開始報告を受領できなかったため管理表から削除したと確認した。実際の削除日、Google Playからの退出・未利用、Console人数・継続日数やテスターのversionCode 10再確認完了を推測しない。詳細は[参加台帳](../../test-specs/closed-testing-14-day-plan.md#61-参加台帳)へ集約する。
- 初回Production公開は段階公開、Managed publishingおよび前版への停止・切戻しを利用できないことを前提に、公開開始条件、Console登録、公開後1時間・6時間・24時間・72時間・7日間の監視、非公開化と前方修正を実行票へ準備済みである。2026-10-31に候補を11へ確定し、修正版は12以上かつ全登録済み番号より大きくするよう更新した。
- versionCode `3`公開後もPre-launch reportは`リリース前レポートを生成するにはアーティファクトをアップロードしてください`の表示から変わっていない。SDK関連警告はなく、ポリシー状態は`問題は見つかりませんでした`である。レポート生成だけを目的とする追加AABは登録せず、Console側の反映を待って再確認する。
- AdMobとGoogle Playのアプリ連携および実広告バナー表示の2026年9月7日の確認記録は履歴として保持する。2026-11-01の最新回答ではストア連携は正式版URL待ち、新規警告・配信制限なし、UMP公開済み。「必須ではありません」の表示から承認済みとは断定せず、個別の準備状況・`app-ads.txt`確認とリンクを一般公開後へ引き継ぐ。
- 本番広告を試験目的でクリックしない。

## 7. 本番公開前後の残りゲート

1. `[完了]` Play Consoleで12人以上・14日間連続のClosed testing要件達成を確認し、台帳を確定する。
2. `[完了・不承認]` Production accessを初回申請し、Googleの指摘を記録する。
3. `[完了]` versionName `1.0.0`、versionCode `6`、公開対象commit、リリースノートおよびUpload Key署名済みAABを確定し、自動検査10/10件を成功させる。
4. `[完了]` Issue #274を含むversionCode `7`を2026年10月6日にClosed testingへ配布し、2026年10月7日にversionCode `5`からの上書き更新とデータ保持を確認した。生成済みversionCode `6`はGoogle Playへ未登録のため配布しない。
5. `[完了]` Closed testing参加者から、versionCode `7`の背景色設定に問題がないとの再確認結果を受けた。

   - `[配布・更新確認・別テスター再確認・詳細条件確認完了]` versionCode `8`の更新・ウィジェット・日付色／テーマ別表示はOK。開発者とは別のT01、2026-10-08、Pixel 9a／Android 16、OKという再確認を記録した。詳細条件と再申請前の人数・期間・警告なしも2026-10-09に確認した。
6. `[承認済み]` 第3回申請（2回目の再申請）のProduction access承認を2026-10-31にUSER確認した。実際の申請・承認日時、提出本文とConsole文言は未記録。過去の不承認履歴を維持する。
7. `[候補確定・Console主要項目と公開Web確認済み・掲載画像差し替え待ち]` 本番候補は既登録の11に固定し、依存更新5件はPR #332で対応済み。2026-11-01の最終確認開始時mainでGitHubゲートは合格し、R3-T16の公開保留はUSER判断で解除した。Consoleの主要項目・画像以外の掲載情報は問題なし、公開Webは正本一致。最新画像14枚への差し替え、レポート表示なしの評価、成果物の安全な別保管・不足試験条件の評価・人的承認が残る。AdMobのストア連携・個別の準備状況と確認は一般公開後へ引き継ぎ、事前の影響評価・了承を公開実行票へ記録する。公開直前の最新GitHubゲートも再照合する。詳細は外部最終確認票を正とし、旧版6の配布、11の再ビルド・再アップロードは行わない。
8. `[未実施]` 最終公開判断後、初期配布地域を日本としてProductionリリースを作成・審査送信・公開し、状態を記録する。アクセス承認をアプリ本体の審査・一般公開完了とは扱わない。
9. 公開後にGoogle Playからの新規インストール、Android vitalsおよびポリシー状態を確認する。
10. 公開日、最終AAB SHA-256、リリースノート、正本・公開サイトのcommitおよびGitタグを記録する。
