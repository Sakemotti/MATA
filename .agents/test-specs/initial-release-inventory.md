# 初回リリース試験棚卸し

- 対象アプリ: MATA `1.0.0 (3)`
- 対象ソースcommit: `307949e2068b1c56ff597c3730b05755b1f37e06`
- 最終更新日: 2026-09-09
- 試験項目書: [MATA 総合動作確認項目書](README.md)
- 項目別結果: [初回リリース試験結果](initial-release-results.tsv)
- 実機実施計画: [MATA 1.0.0 (2) RELEASE_OWNER実機試験実施計画](release-owner-device-test-plan.md)
- Closed testing台帳: [初回Closed testing実施台帳](closed-testing-log.md)
- リリース状況: [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md)

## 1. 判定

現時点では、versionCode `2`のGoogle Play配布、主要実機試験、AdMob連携と実広告バナー表示を確認し、Closed testingフィードバック#162・#164～#166を反映したversionCode `3`もClosed testingへ公開して上書き更新に成功している。一方、修正内容固有の再試験、全P0/P1、必須環境マトリクス、Pre-launch reportおよびClosed testing期間の証跡が未完了のため、本番公開判定は`保留`とする。

これは不具合による不合格ではなく、項目単位の証跡が不足している状態である。

## 2. 試験項目の母数

| 区分 | P0 | P1 | P2 | 合計 |
| --- | ---: | ---: | ---: | ---: |
| アプリ横断・リリース | 115 | 30 | 0 | 145 |
| TODO一覧 | 21 | 17 | 4 | 42 |
| TODO登録・編集 | 26 | 16 | 1 | 43 |
| カレンダー履歴 | 18 | 22 | 2 | 42 |
| カテゴリ管理 | 10 | 23 | 5 | 38 |
| アーカイブ済みTODO | 22 | 24 | 1 | 47 |
| 設定 | 29 | 20 | 3 | 52 |
| カテゴリ別TODO一覧 | 7 | 4 | 0 | 11 |
| 合計 | 248 | 156 | 16 | 420 |

リリース必須のP0/P1は404件である。各項目書の`結果`列は再利用可能な原本として全件`未実施`のまま維持し、リリース候補ごとの実績は[項目別結果TSV](initial-release-results.tsv)へ記録する。2026年9月9日時点では、項目単位の証跡が揃ったP0を141件、P1を38件`合格`、P0/P1の残り225件を`未実施`として登録した。P2を含む全420件では179件が合格、241件が未実施である。versionCode 3向け17件、広告読込失敗時の`WGT-014`、および環境別7セッションはすべて合格した。

## 3. 自動検査の証跡

| 対象 | 結果 | 証跡 | 対応する主な項目 |
| --- | --- | --- | --- |
| 単体テスト | 41 test suite、176件、失敗0・エラー0・スキップ0 | 2026-09-09のローカル`testDebugUnitTest`結果、[PR #124 CI run 33859640271](https://github.com/Sakemotti/MATA/actions/runs/33859640271) | `REL-001`、論理日・繰り返し・通知・ViewModel等の一部 |
| Debug検証 | `testDebugUnitTest`、`lintDebug`、`assembleDebug`成功 | [main CI run 33751810640](https://github.com/Sakemotti/MATA/actions/runs/33751810640) | `REL-005`〜`REL-007` |
| Instrumented test | API 34 x86_64で現行116件完了、失敗なし。API 30 x86_64の既存54件も失敗なし | 2026-09-09のローカル`:app:connectedDebugAndroidTest`結果、[PR #126 CI run 33867709111](https://github.com/Sakemotti/MATA/actions/runs/33867709111) | `REL-027`、Room Migration・Repository・通知・設定変更・バックアップ・Compose UIの一部 |
| Release検証 | `lintRelease`、成果物メタデータ生成、Manifest security、成果物検証が成功 | main CI run 33751810640 | `REL-020`、`REL-025`の正常系 |
| 性能用成果物 | Benchmark APKとMacrobenchmark APKの生成成功 | main CI run 33751810640 | 性能試験を開始できることの確認 |
| リポジトリ検査 | 秘密情報・署名ファイル検査、法的サイト検証、Play掲載情報検証が成功 | main CI run 33751810640 | `REL-015`と`REL-018`の自動検査部分 |
| リリース準備検査 | ドラフト検証成功 | 2026-09-04に`node tools/release/verify-readiness.mjs`を実行 | `REL-019` |
| Upload Key設定ガード | 秘密値の一部設定、相対パス、リポジトリ内ファイル、署名必須フラグなし、Configuration Cache有効化の5異常系を拒否し、例外へ架空の秘密値を含めないことを確認 | 2026-09-04に`:app:verifyUploadSigningGuards`を実行 | `REL-023` |
| 署名済みAAB | Upload KeyのSHA-256が成果物とPlay Consoleで一致 | [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md) | `REL-024` |
| versionCode 2公開候補 | クリーンなmainからUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (2) 公開候補生成結果](release-candidate-1.0.0-2.md) | `REL-021` |
| versionCode 3公開候補 | commit `307949e2068b1c56ff597c3730b05755b1f37e06`からUpload Key署名済みAABと関連成果物を生成し、Releaseモードの全7検査が成功 | [MATA 1.0.0 (3) 公開候補生成結果](release-candidate-1.0.0-3.md) | `REL-021` |
| versionCode 3期限日・繰り越し回帰 | 単発TODOの実行可能期間と期限、繰り越し状態の生成・解決・編集、通知候補、Room 7→8移行、バックアップ形式1〜4の互換性を専用テスト8件で検証 | 2026年9月9日にローカルJDK 21の全176件、API 34 x86_64エミュレータの全116件、および[PR #172のCI](https://github.com/Sakemotti/MATA/actions/runs/34317723204)が成功 | `DAY-015`、`DAY-016`、`STA-013`〜`STA-015`、`NTF-016`、`DAT-011`、`DAT-012` |
| Release成果物改変検出 | 正常系1件とSBOMの内容・パス・欠損・容量・SHA-256・必須component・依存グラフの異常系6件が成功 | 2026-09-04に`node --test tools/release/release-artifact-verifier.test.mjs`を実行 | `REL-026` |
| 論理日・繰り返し計算 | 試験IDを接頭辞に持つ専用JUnitテスト26件が成功し、ID・テストメソッド・実行タスクの1対1対応を機械検証 | 2026-09-04に`:app:testDebugUnitTest --tests com.mochisofts.mata.domain.model.ScheduleTestSpecCoverageTest`と`verify-automated-evidence.mjs`を実行 | `DAY-001`〜`DAY-003`、`DAY-006`〜`DAY-008`、`DAY-013`、`RPT-001`〜`RPT-004`、`RPT-007`〜`RPT-018`、`RPT-027`〜`RPT-029` |
| 履歴・復元・完全削除 | 終了済み論理日の履歴確定、回数期間の達成・未達成、履歴取り消し範囲、復元時の基準と埋め戻し防止、関連データ完全削除をRoom上の専用instrumented test 8件で検証 | `:app:connectedDebugAndroidTest`と`verify-automated-evidence.mjs`を実行 | `DAY-010`、`RPT-024`、`STA-003`、`STA-010`、`STA-011`、`AT-016`、`AT-017`、`AT-028` |
| 通知候補・登録・再構成 | 分・時間・日単位の候補計算と、複数登録、権限変更、exact切替、再起動・時刻・設定変更、無効設定、アーカイブ・削除、冪等性を専用テスト11件で検証 | [PR #124 CI run 33859640271](https://github.com/Sakemotti/MATA/actions/runs/33859640271)の`:app:testDebugUnitTest`とAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `NTF-001`、`NTF-002`、`NTF-006`、`NTF-007`、`NTF-009`〜`NTF-015` |
| 設定変更時の再計算・履歴不変 | 週開始曜日変更後の現在期間・必要数・完了数・残数の即時再計算と、終了時刻・週開始曜日変更後の確定済み履歴・期間スナップショット不変を専用instrumented test 2件で検証 | [PR #125 CI run 33863729049](https://github.com/Sakemotti/MATA/actions/runs/33863729049)のAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `ST-010`、`ST-011` |
| バックアップ形式・内容・事前検証 | 全種ユーザーデータ、除外対象、復元前の形式・ハッシュ・構造・型・範囲・参照・互換性検証、ファイル名、ZIP内部メタデータを専用instrumented test 5件で検証 | [PR #126 CI run 33867709111](https://github.com/Sakemotti/MATA/actions/runs/33867709111)のAPI 30`:app:connectedDebugAndroidTest`、`verify-automated-evidence.mjs` | `ST-019`、`ST-020`、`ST-023`、`ST-D02`、`ST-D03` |

[自動試験証跡TSV](automated-test-evidence.tsv)へ登録した専用テスト108件は試験IDと1対1に関連付ける。それ以外の自動テスト名と試験IDは現状1対1で機械的に関連付けられていないため、対応領域の証跡として利用しても、関連する全項目を自動的に合格扱いにはしない。

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
