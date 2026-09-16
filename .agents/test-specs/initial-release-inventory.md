# 初回リリース試験棚卸し

- 対象アプリ: MATA `1.0.0 (5)`
- 対象ソースcommit: `09cbbc85d9c8ecb9db3137cbd62e03c22b8dcb0d`
- 最終更新日: 2026-09-16
- 試験項目書: [MATA 総合動作確認項目書](README.md)
- 項目別結果: [初回リリース試験結果](initial-release-results.tsv)
- 実機実施計画: [MATA 1.0.0 (2) RELEASE_OWNER実機試験実施計画](release-owner-device-test-plan.md)
- Closed testing台帳: [初回Closed testing実施台帳](closed-testing-log.md)
- リリース状況: [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md)

## 1. 判定

現時点では、versionCode `2`と`3`のGoogle Play配布、主要実機試験、AdMob連携と実広告バナー表示を確認し、カードレイアウト改善を反映したversionCode `5`の署名済み候補も生成済みである。一方、versionCode `5`のClosed testing登録と上書き更新、全P0/P1、Pre-launch reportおよびClosed testing期間の証跡が未完了のため、本番公開判定は`保留`とする。

これは不具合による不合格ではなく、項目単位の証跡が不足している状態である。

## 2. 試験項目の母数

| 区分 | P0 | P1 | P2 | 合計 |
| --- | ---: | ---: | ---: | ---: |
| アプリ横断・リリース | 115 | 30 | 0 | 145 |
| TODO一覧 | 21 | 18 | 5 | 44 |
| TODO登録・編集 | 26 | 16 | 1 | 43 |
| カレンダー履歴 | 18 | 22 | 3 | 43 |
| カテゴリ管理 | 10 | 23 | 5 | 38 |
| アーカイブ済みTODO | 22 | 24 | 1 | 47 |
| 設定 | 29 | 20 | 3 | 52 |
| カテゴリ別TODO一覧 | 7 | 4 | 0 | 11 |
| 合計 | 248 | 157 | 18 | 423 |

リリース必須のP0/P1は405件である。各項目書の`結果`列は再利用可能な原本として全件`未実施`のまま維持し、リリース候補ごとの実績は[項目別結果TSV](initial-release-results.tsv)へ記録する。

2026年9月16日時点では、項目単位の証跡が揃ったP0を244件、P1を147件`合格`、P0/P1の残り14件を`未実施`として登録した。P2を含む全423件では394件が合格、29件が未実施である。今回はカテゴリ管理の16色固定パレット、テーマ別色トーン、用途別Material Icons選択・正規化検索、色・アイコン仕様表照合を追加合格とした。

## 3. 自動検査の証跡

| 対象 | 結果 | 証跡 | 対応する主な項目 |
| --- | --- | --- | --- |
| 単体テスト | 41 test suite、205件、失敗0・エラー0・スキップ0 | 2026-09-14のローカル`testDebugUnitTest`結果、[PR #212 CI run 34799349988](https://github.com/Sakemotti/MATA/actions/runs/34799349988) | `REL-001`、論理日・繰り返し・通知・ViewModel等の一部 |
| Debug検証 | `testDebugUnitTest`、`lintDebug`、`assembleDebug`成功 | [main CI run 33751810640](https://github.com/Sakemotti/MATA/actions/runs/33751810640) | `REL-005`〜`REL-007` |
| Instrumented test | API 30 x86_64で現行207件完了、失敗なし。API 34 x86_64でも142件時点で失敗なし | [PR #213 CI run 34808229109](https://github.com/Sakemotti/MATA/actions/runs/34808229109)、2026-09-12のローカル`:app:connectedDebugAndroidTest`結果 | `REL-027`、Room Migration・Repository・通知・設定変更・バックアップ・Compose UIの一部 |
| Release検証 | `lintRelease`、成果物メタデータ生成、Manifest security、成果物検証が成功 | main CI run 33751810640 | `REL-020`、`REL-025`の正常系 |
| 性能用成果物 | Benchmark APKとMacrobenchmark APKの生成成功 | main CI run 33751810640 | 性能試験を開始できることの確認 |
| リポジトリ検査 | 秘密情報・署名ファイル検査、法的サイト検証、Play掲載情報検証が成功 | main CI run 33751810640 | `REL-015`と`REL-018`の自動検査部分 |
| リリース準備検査 | ドラフト検証成功 | 2026-09-04に`node tools/release/verify-readiness.mjs`を実行 | `REL-019` |
| Upload Key設定ガード | 秘密値の一部設定、相対パス、リポジトリ内ファイル、署名必須フラグなし、Configuration Cache有効化の5異常系を拒否し、例外へ架空の秘密値を含めないことを確認 | 2026-09-04に`:app:verifyUploadSigningGuards`を実行 | `REL-023` |
| 署名済みAAB | Upload KeyのSHA-256が成果物とPlay Consoleで一致 | [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md) | `REL-024` |
| versionCode 2公開候補 | クリーンなmainからUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (2) 公開候補生成結果](release-candidate-1.0.0-2.md) | `REL-021` |
| versionCode 3公開候補 | commit `307949e2068b1c56ff597c3730b05755b1f37e06`からUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (3) 公開候補生成結果](release-candidate-1.0.0-3.md) | `REL-021` |
| versionCode 4公開候補 | commit `fe71e1579969d647e95170e47f2d036b19e5b657`からUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (4) 公開候補生成結果](release-candidate-1.0.0-4.md) | `REL-021` |
| versionCode 5公開候補 | commit `09cbbc85d9c8ecb9db3137cbd62e03c22b8dcb0d`からUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (5) 公開候補生成結果](release-candidate-1.0.0-5.md) | `REL-021` |
| versionCode 3期限日・繰り越し回帰 | 単発TODOの実行可能期間と期限、繰り越し状態の生成・解決・編集、通知候補、Room 7→8移行、バックアップ形式1〜4の互換性を専用テスト8件で検証 | 2026年9月9日にローカルJDK 21の全176件、API 34 x86_64エミュレータの全116件、および[PR #172のCI](https://github.com/Sakemotti/MATA/actions/runs/34317723204)が成功 | `DAY-015`、`DAY-016`、`STA-013`〜`STA-015`、`NTF-016`、`DAT-011`、`DAT-012` |
| 制御異常系 | 一覧・フォーム・外部状態の読込失敗と再試行、空状態、操作失敗、検索・月切替の古い応答破棄、広告失敗時の空白非確保を専用テスト9件で検証 | 2026年9月9日のローカル`testDebugUnitTest`成功 | `TL-024`、`TE-D06`、`CH-027`、`CH-030`、`CM-029`、`CTL-008`、`AT-005`、`ST-039`、`ST-042` |
| TODO一覧の中核表示 | カテゴリ順、実期限順、期限なし表示、高速な日付切替、完了ボタン位置を専用テスト5件で検証 | 2026年9月9日のローカル`testDebugUnitTest`とAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `TL-008`、`TL-009`、`TL-011`、`TL-031`、`TL-032` |
| TODO一覧の状態・操作 | 過去日の全状態と読み取り専用操作、未来日の操作制限、完了・スキップの即時反映と取り消し非提供、完了済み操作禁止、アーカイブ・完全削除の事前説明、過去日の追加禁止、非表示状態を含む進捗集計を専用テスト8件で検証 | 2026年9月13日のローカル`testDebugUnitTest`とPR CIのAPI 30`:app:connectedDebugAndroidTest`成功 | `TL-005`、`TL-006`、`TL-013`〜`TL-015`、`TL-017`、`TL-020`、`TL-036` |
| TODO一覧の初期化・再評価・固定表示 | 新規表示時の日付初期化、カテゴリ追加・編集・削除・並び替えの即時再評価、操作有無によらない本文領域、期限なしの内部並びと表示、再表示時の日付・スクロール位置初期化を専用テスト5件で検証 | [PR #211 CI run 34793793877](https://github.com/Sakemotti/MATA/actions/runs/34793793877)のAPI 30`:app:connectedDebugAndroidTest`と`testDebugUnitTest`成功 | `TL-002`、`TL-026`、`TL-029`、`TL-D04`、`TL-D06` |
| TODO一覧の画面遷移・論理日・履歴詳細 | 通常表示と現在地ドロワー、深夜帯の共通論理日と行の日付、週・月回数指定TODOの同日二重完了拒否、過去履歴詳細の表示・操作制限、定義済み更新契機だけでの論理日・期限再評価を専用テスト6件で検証 | [PR #212 CI run 34799349988](https://github.com/Sakemotti/MATA/actions/runs/34799349988)のAPI 30`:app:connectedDebugAndroidTest`と`testDebugUnitTest`成功 | `TL-001`、`TL-003`、`TL-016`、`TL-018`、`TL-021` |
| TODO一覧の日付操作・カテゴリ・行表示 | 前後ボタン・左右スワイプ・DatePicker・今日復帰、カテゴリ見出しの名称・色・アイコン、長文2行制限と全状態情報、期限超過の配色と行背景維持を専用Compose UI test 4件で検証 | [PR #213 CI run 34808229109](https://github.com/Sakemotti/MATA/actions/runs/34808229109)のAPI 30`:app:connectedDebugAndroidTest`成功 | `TL-004`、`TL-007`、`TL-010`、`TL-012` |
| カレンダー中核計算 | 全週開始曜日、6週グリッド、日別件数、日状態優先順位、期間結果マーカーの独立性を専用テスト5件で検証 | 2026年9月11日のローカル`testDebugUnitTest`成功 | `CH-006`、`CH-007`、`CH-009`、`CH-012`、`CH-D04` |
| カレンダー履歴リポジトリ | 回数型の操作日集計、対象論理日への帰属、週・月期間結果、履歴の安定順序をRoom上の専用instrumented test 4件で検証 | [PR #180 CI run 34557570307](https://github.com/Sakemotti/MATA/actions/runs/34557570307)のAPI 30`:app:connectedDebugAndroidTest`成功 | `CH-010`、`CH-011`、`CH-013`、`CH-017` |
| カレンダー履歴の基本操作・表示 | 初期日付、今日へ戻る、未来選択禁止、日サマリー、セクション順、長文行、日・期間詳細、空状態を専用テスト8件で検証 | [PR #200 CI run 34748557262](https://github.com/Sakemotti/MATA/actions/runs/34748557262)の`testDebugUnitTest`とAPI 30`:app:connectedDebugAndroidTest`成功 | `CH-002`、`CH-004`、`CH-005`、`CH-015`、`CH-016`、`CH-018`、`CH-021`、`CH-026` |
| カレンダー履歴の月移動・データ保全・取り消し | ボタン・スワイプ・年月選択による月移動、アーカイブ・完全削除後の履歴、取り消し可否・再計算・5秒以内の復元・読み取り専用操作を専用テスト8件で検証 | [PR #201 CI run 34751041027](https://github.com/Sakemotti/MATA/actions/runs/34751041027)のAPI 30`:app:connectedDebugAndroidTest`成功 | `CH-003`、`CH-019`、`CH-020`、`CH-022`〜`CH-025`、`CH-D05` |
| カレンダー履歴の画面遷移・再読込・状態・レイアウト | ドロワー遷移、データ・設定変更と明示更新、不要操作の非表示、固定カレンダーとスクロール領域、未来月禁止、再生成時の初期化、2種類の詳細レイアウトを専用テスト7件で検証 | [PR #202 CI run 34753189344](https://github.com/Sakemotti/MATA/actions/runs/34753189344)の`testDebugUnitTest`とAPI 30`:app:connectedDebugAndroidTest`成功 | `CH-001`、`CH-031`、`CH-033`、`CH-D01`、`CH-D02`、`CH-D06`、`CH-D07` |
| カレンダー履歴のテーマ・再生成・最大表示・無効日 | ライト・ダーク・Dynamic Colorの視認性、一時再生成と通常再表示の状態差、最大フォント・コンパクト幅、月外日と未来日の視覚・semanticsを専用テスト4件で検証 | Android Emulator API 34 x86_64の`:app:connectedDebugAndroidTest`成功 | `CH-008`、`CH-032`、`CH-035`、`CH-D03` |
| カテゴリ別TODO一覧 | タブ順と切替、定義単位表示、今日の3状態、対象外表示、編集遷移、状態不変、削除カテゴリからの復帰、状態欄の固定幅を専用instrumented test 8件で検証 | 2026年9月11日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `CTL-002`〜`CTL-007`、`CTL-009`、`CTL-011` |
| アーカイブ一覧・履歴照会 | 定義単位表示、現在値検索、種別別履歴件数、3並びモードの補助キー、検索正規化と履歴除外、同日履歴の安定順序をRoom上の専用instrumented test 6件で検証 | 2026年9月11日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `AT-002`、`AT-004`、`AT-011`、`AT-D01`、`AT-D02`、`AT-D04` |
| アーカイブ画面UI | 主要画面と現在地、並び順の切替・永続復元、長文一覧行、全件・検索0件の区別、全項目を持つ3カード詳細、一覧・詳細操作、大量混在履歴の追加取得と読み取り専用詳細、現在・将来再開と埋め戻し禁止、終了済み警告、通知停止理由、一覧・詳細復元後の遷移、完全削除の件数・範囲・不可逆性、バックアップ中・連打時の排他制御、画面外更新への追従、通常再表示時の初期化、一時再生成時の状態・ダイアログ・二重実行防止、最大フォント・コンパクト表示、追加取得中の固定操作、長文履歴詳細と終了操作をCompose UI test 24件で検証 | 2026年9月16日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `AT-001`、`AT-003`、`AT-006`〜`AT-010`、`AT-012`〜`AT-015`、`AT-022`、`AT-024`〜`AT-027`、`AT-032`〜`AT-036`、`AT-040`、`AT-D03`、`AT-D07` |
| TODO編集の入力・繰り返し | タイトル・説明上限、過去日禁止、全10方式の境界、期限時刻の論理日変換、毎月第X曜日、N週間にX回、曜日プリセットと個別変更後の祝日条件解除、月末丸めを専用ユニットテスト9件で検証 | 2026年9月13日のローカル`:app:testDebugUnitTest`と[PR #204 CI run 34758657480](https://github.com/Sakemotti/MATA/actions/runs/34758657480)成功 | `TE-003`、`TE-007`、`TE-009`、`TE-015`、`TE-031`〜`TE-033`、`TE-D04`、`RPT-031` |
| TODO編集の通知・プレビュー | カテゴリ非依存の論理日、祝日暫定値、通知上限・重複・日末検証、通知順序、過去候補、権限拒否、通常通知フォールバック、連続編集時の再計算を専用テスト8件で検証 | 2026年9月12日のローカル`:app:testDebugUnitTest`とPR CIのAPI 30`:app:connectedDebugAndroidTest`成功 | `TE-006`、`TE-012`、`TE-016`〜`TE-020`、`TE-D03` |
| TODO編集の基本画面・保存境界 | 新規・編集モード、初期値と操作メニュー、カテゴリ順と新規作成、単発・繰り返しの日付UI、次回予定と回数進捗、方式別一時値、全週開始曜日と部分期間、実変更だけの保存可否、保存結果、未保存確認、再生成時の下書き・位置復元、初期フォーカスを専用unit/Compose UI test 13件で検証 | 2026年9月15日のローカル`testDebugUnitTest`とAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `TE-001`、`TE-002`、`TE-004`、`TE-005`、`TE-008`、`TE-010`、`TE-013`、`TE-014`、`TE-024`、`TE-026`、`TE-027`、`TE-029`、`TE-D02` |
| カテゴリ管理の並び替え・名称・履歴整合性 | ドロップ保存と再表示、一覧・タブへの順序反映、名称の境界値・正規化・表記保持、編集・削除後の現在参照と確定済み履歴スナップショットを専用テスト6件で検証 | 2026年9月12日のローカル`:app:testDebugUnitTest`とPR CIのAPI 30`:app:connectedDebugAndroidTest`成功 | `CM-007`、`CM-010`、`CM-012`、`CM-013`、`CM-021`、`CM-023` |
| カテゴリ管理の基本表示・遷移 | ドロワーからの全画面遷移と選択状態、カテゴリ未設定の除外、空状態とFABからの追加、既存値の編集読込、フォーム構成、新規既定値を専用Compose UI test 6件で検証 | [PR #203 CI run 34756112549](https://github.com/Sakemotti/MATA/actions/runs/34756112549)のAPI 30`:app:connectedDebugAndroidTest`成功 | `CM-001`、`CM-002`、`CM-005`、`CM-006`、`CM-011`、`CM-D01` |
| カテゴリ管理の操作・破棄 | TalkBack用カスタム並べ替え、削除成功通知と取り消し非提供、変更時だけの破棄確認、名称プレビューと無効入力継続を専用Compose UI test 4件で検証 | [PR #204 CI run 34758657480](https://github.com/Sakemotti/MATA/actions/runs/34758657480)のAPI 30`:app:connectedDebugAndroidTest`成功 | `CM-009`、`CM-026`、`CM-027`、`CM-D03` |
| カテゴリ管理の色・アイコンカタログ | 16色の固定ID・日本語名・基準色・テーマ別トーンと選択状態、全47 Material IconsのID・用途別表示・日本語名・正規化検索・カスタム入力非提供を専用Compose UI test 5件で検証 | 2026年9月16日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `CM-014`〜`CM-016`、`CM-D04`、`CM-D05` |
| 設定画面の基本表示・選択 | セクション順、設定行種別、終了時刻24候補、週開始7候補、バックアップ警告、広告・購入UI非表示、アプリ情報、Debug表記を専用Compose UI test 9件で検証 | [PR #205 CI run 34762842700](https://github.com/Sakemotti/MATA/actions/runs/34762842700)のAPI 30`:app:connectedDebugAndroidTest`成功 | `ST-002`、`ST-003`、`ST-005`、`ST-009`、`ST-022`、`ST-030`、`ST-031`、`ST-037`、`ST-038` |
| 設定画面のUMPプライバシーオプション | UMP要求有無による行表示、アプリ情報内の配置、フォーム起動、同意変更時の既存広告破棄と再評価、エラー時の画面維持を専用Compose UI test 5件で検証 | 2026年9月15日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `ST-032`〜`ST-036` |
| 設定画面のナビゲーション・論理日説明・復元確認 | 設定の選択状態、他画面への重複導線なし、終了時刻別の説明、復元対象の内訳と不可逆警告、開始前後の取消可否を専用Compose UI test 5件で検証 | 2026年9月15日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `ST-001`、`ST-006`、`ST-017`、`ST-025`、`ST-D04` |
| 設定画面の共有設定・復元結果・外部連携境界 | 完了済み表示の画面間共有、無効バックアップ理由、復元成功後の復帰、法的文書リンク、広告同意のバックアップ除外を専用unit/instrumented test 5件で検証 | 2026年9月15日のGradle unit testとAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `ST-012`、`ST-024`、`ST-027`、`ST-040`、`ST-D06` |
| 設定画面の終了境界・バックアップ障害 | 終了時刻変更による全カテゴリ再計算と無効通知確認、SAF保存、容量不足・I/O・中断時の後始末を専用instrumented test 4件で検証 | 2026年9月15日のAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `ST-007`、`ST-008`、`ST-018`、`ST-021` |
| アプリ共通の構成・データ境界 | 自動バックアップ無効、SDK・アプリID、外部通信からのユーザー入力分離、SAF限定ストレージアクセス、非暗号化バックアップと警告を専用unit/instrumented test 5件で検証 | 2026年9月15日のGradle unit testとAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `APP-007`、`APP-013`、`DAT-002`、`DAT-004`、`DAT-010` |
| 完了・スキップの取り消し境界 | 一覧・通知・ウィジェットには取り消し導線を設けず履歴だけに限定する構成と、スキップ取り消し後に日集計・当日一覧・通知再計算・ウィジェット表示が未完了へ戻ることを専用unit/instrumented test 2件で検証 | 2026年9月15日のGradle unit testとAPI 34 x86_64`:app:connectedDebugAndroidTest`成功 | `STA-002`、`STA-005` |
| Release成果物改変検出 | 正常系1件とSBOMの内容・パス・欠損・容量・SHA-256・必須component・依存グラフの異常系6件が成功 | 2026-09-04に`node --test tools/release/release-artifact-verifier.test.mjs`を実行 | `REL-026` |
| 論理日・繰り返し計算 | 試験IDを接頭辞に持つ専用JUnitテスト26件が成功し、ID・テストメソッド・実行タスクの1対1対応を機械検証 | 2026-09-04に`:app:testDebugUnitTest --tests com.mochisofts.mata.domain.model.ScheduleTestSpecCoverageTest`と`verify-automated-evidence.mjs`を実行 | `DAY-001`〜`DAY-003`、`DAY-006`〜`DAY-008`、`DAY-013`、`RPT-001`〜`RPT-004`、`RPT-007`〜`RPT-018`、`RPT-027`〜`RPT-029` |
| 履歴・復元・完全削除 | 終了済み論理日の履歴確定、回数期間の達成・未達成、履歴取り消し範囲、復元時の基準と埋め戻し防止、開始前・終了後の復元境界、関連データとカレンダー集計の完全削除をRoom上の専用instrumented test 10件で検証 | `:app:connectedDebugAndroidTest`と`verify-automated-evidence.mjs`を実行 | `DAY-010`、`RPT-024`、`STA-003`、`STA-010`、`STA-011`、`AT-016`、`AT-017`、`AT-020`、`AT-028`、`AT-030` |
| 通知候補・登録・再構成 | 分・時間・日単位の候補計算と、複数登録、権限変更、exact切替、再起動・時刻・設定変更、無効設定、アーカイブ・削除、冪等性を専用テスト11件で検証 | [PR #124 CI run 33859640271](https://github.com/Sakemotti/MATA/actions/runs/33859640271)の`:app:testDebugUnitTest`とAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `NTF-001`、`NTF-002`、`NTF-006`、`NTF-007`、`NTF-009`〜`NTF-015` |
| 設定変更時の再計算・履歴不変 | 週開始曜日変更後の現在期間・必要数・完了数・残数の即時再計算と、終了時刻・週開始曜日変更後の確定済み履歴・期間スナップショット不変を専用instrumented test 2件で検証 | [PR #125 CI run 33863729049](https://github.com/Sakemotti/MATA/actions/runs/33863729049)のAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `ST-010`、`ST-011` |
| バックアップ形式・内容・事前検証 | 全種ユーザーデータ、除外対象、復元前の形式・ハッシュ・構造・型・範囲・参照・互換性検証、ファイル名、ZIP内部メタデータを専用instrumented test 5件で検証 | [PR #126 CI run 33867709111](https://github.com/Sakemotti/MATA/actions/runs/33867709111)のAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `ST-019`、`ST-020`、`ST-023`、`ST-D02`、`ST-D03` |

[自動試験証跡TSV](automated-test-evidence.tsv)へ登録した専用テスト322件は試験IDと1対1に関連付ける。それ以外の自動テスト名と試験IDは現状1対1で機械的に関連付けられていないため、対応領域の証跡として利用しても、関連する全項目を自動的に合格扱いにはしない。

## 4. 実機・Console・Web確認の証跡

| 確認範囲 | 状態 | 項目別判定で不足する情報 |
| --- | --- | --- |
| Google Playからの新規インストールと起動 | 確認済み | 端末名、Android API、対象日時の記録。API 26指定の`APP-001`は未確定 |
| TODO等の主要機能 | 現状問題なし | 実行した試験ID、条件、操作、結果の対応付け |
| 通知・ウィジェット | 現状問題なし | 権限状態、正確なアラーム、再起動、日時変更等の条件別記録 |
| 手動バックアップ・復元 | 現状問題なし | 正常系以外の破損・不整合・旧形式等の条件別記録 |
| 法的ページへのアプリ内遷移 | 確認済み | なし。ただし法的サイト更新時は再確認する |
| 公開URLと`app-ads.txt` | HTTPS 200と内容一致を確認済み | スマートフォン幅・JavaScript無効の確認は未記録 |
| Play App Signing | Upload Key証明書一致を確認済み | なし |
| ストア掲載情報とApp content | 登録済み、未完了カードなし | 公開候補確定時の最終差分確認 |
| AdMob連携と実広告バナー表示 | 2026年9月7日に確認済み | UMP同意状態、読込失敗、オフライン、全広告配置および確認端末・版の条件別記録 |

端末・OS・日時を特定できない既存の「問題なし」は、関連領域の`一部確認`として扱う。

## 5. リリース項目の現在位置

| 状態 | 試験ID | 根拠または残作業 |
| --- | --- | --- |
| 証跡あり | `REL-001`〜`REL-007`、`REL-009`、`REL-011`〜`REL-027` | 第3節の自動検査、公開候補、静的レビュー、外部送信・法的サイト同期照合および署名証明書照合 |
| 一部確認 | なし | - |
| 未完了 | `REL-008`、`REL-010` | [REL-008専用手順](rel-008-visual-check.md)で全テーマ・最大フォントを実機確認し、全P0/P1を集計する |

`REL-010`は他の全P0/P1が揃った時点で最後に判定する。

## 6. Closed testing中の確認順

未実施P0/P1の全体的なセッション分割、環境および実施順は[RELEASE_OWNER実機試験実施計画](release-owner-device-test-plan.md)、versionCode 3変更内容と不足環境の優先確認には[残実機・環境試験計画](release-v3-device-verification-plan.md)を使用する。

1. Closed testing参加者には試験項目を渡さず、複数日にわたって自由操作してもらう。
2. テスターごとに端末名、Androidバージョン、アプリversion、参加・最終利用確認日および自由記述のフィードバックを記録する。
3. `OWNER`がClosed testingとは別に、P0の基本フロー、データ整合性、論理日・繰り返し、通知、ウィジェット、バックアップを正式確認する。
4. `OWNER`がAPI 26、33、36、スマートフォン、タブレット、分割画面、ライト・ダーク、最大フォント、TalkBackの不足枠を埋める。
5. UMP同意、バナー配置、読込失敗、オフライン、ウィジェット操作画面の広告を確認する。本番広告はクリックしない。
6. versionCode `2`以上を配信する最初の機会に、Google Play経由の上書き更新と既存データ・通知・ウィジェットの維持を確認する。
7. Pre-launch report、SDK Index、権限、Data safety、ポリシー警告を最終確認する。
8. 全P0/P1の結果を集計し、`REL-010`と本番公開判定を更新する。

## 7. 証跡の記録形式

Closed testingの自由操作記録には、最低限、次を含める。試験IDは`OWNER`が別途正式確認した場合だけ記録する。

| 項目 | 記入内容 |
| --- | --- |
| 試験ID | 例: `WGT-005` |
| アプリ | versionName、versionCode、可能ならソースcommit |
| 環境 | 端末名、Androidバージョン、画面状態、テーマ、文字サイズ、権限、ネットワーク |
| 実施 | 日時、実施者、事前データ、操作 |
| 結果 | `合格`、`不合格`、`保留`、`対象外` |
| 証跡 | スクリーンショット、画面録画、ログ、Issue番号 |

同じビルド・端末・条件で連続実施した項目は、環境情報を共通化して複数IDをまとめて記録してよい。
