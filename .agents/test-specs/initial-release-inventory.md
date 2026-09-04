# 初回リリース試験棚卸し

- 対象アプリ: MATA `1.0.0 (1)`
- 対象ソースcommit: `e57ababd3b6fb4ad12bf57dada776e9189288dbc`
- 棚卸し日: 2026-09-04
- 試験項目書: [MATA 総合動作確認項目書](README.md)
- 項目別結果: [MATA 1.0.0 (1) 試験結果](initial-release-results.tsv)
- Closed testing台帳: [初回Closed testing実施台帳](closed-testing-log.md)
- リリース状況: [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md)

## 1. 判定

現時点では、既知の重大な不具合はなく、Internal testing版で主要機能を利用できている。ただし、全P0/P1項目の項目別結果、必須環境マトリクス、広告、上書き更新、Pre-launch reportおよびClosed testingの証跡が揃っていないため、本番公開判定は`保留`とする。

これは不具合による不合格ではなく、項目単位の証跡が不足している状態である。

## 2. 試験項目の母数

| 区分 | P0 | P1 | P2 | 合計 |
| --- | ---: | ---: | ---: | ---: |
| アプリ横断・リリース | 106 | 30 | 0 | 136 |
| TODO一覧 | 18 | 17 | 4 | 39 |
| TODO登録・編集 | 23 | 15 | 1 | 39 |
| カレンダー履歴 | 18 | 22 | 2 | 42 |
| カテゴリ管理 | 10 | 22 | 5 | 37 |
| アーカイブ済みTODO | 22 | 24 | 1 | 47 |
| 設定 | 29 | 20 | 3 | 52 |
| カテゴリ別TODO一覧 | 7 | 4 | 0 | 11 |
| 合計 | 233 | 154 | 16 | 403 |

リリース必須のP0/P1は387件である。各項目書の`結果`列は再利用可能な原本として全件`未実施`のまま維持し、リリース候補ごとの実績は[項目別結果TSV](initial-release-results.tsv)へ記録する。2026年9月4日時点では、項目単位の証跡が揃ったP0を35件、P1を3件`合格`、残り365件を`未実施`として登録した。広い範囲をまとめた実機確認だけから個々の項目を一律に`合格`とは扱わない。

## 3. 自動検査の証跡

| 対象 | 結果 | 証跡 | 対応する主な項目 |
| --- | --- | --- | --- |
| 単体テスト | 36 test suite、134件、失敗0・エラー0・スキップ0 | 2026-09-03のローカル`testDebugUnitTest`結果、対象commitを含むmain CI | `REL-001`、論理日・繰り返し・ViewModel等の一部 |
| Debug検証 | `testDebugUnitTest`、`lintDebug`、`assembleDebug`成功 | [main CI run 33751810640](https://github.com/Sakemotti/MATA/actions/runs/33751810640) | `REL-005`〜`REL-007` |
| Instrumented test | API 30 x86_64で34件完了、失敗なし | [PR #116 CI run 33750730765](https://github.com/Sakemotti/MATA/actions/runs/33750730765)。ログに`Starting 34 tests`、`Finished 34 tests`、`BUILD SUCCESSFUL` | `REL-027`、Room Migration・Repository・Compose UIの一部 |
| Release検証 | `lintRelease`、成果物メタデータ生成、Manifest security、成果物検証が成功 | main CI run 33751810640 | `REL-020`、`REL-025`の正常系 |
| 性能用成果物 | Benchmark APKとMacrobenchmark APKの生成成功 | main CI run 33751810640 | 性能試験を開始できることの確認 |
| リポジトリ検査 | 秘密情報・署名ファイル検査、法的サイト検証、Play掲載情報検証が成功 | main CI run 33751810640 | `REL-015`と`REL-018`の自動検査部分 |
| リリース準備検査 | ドラフト検証成功 | 2026-09-04に`node tools/release/verify-readiness.mjs`を実行 | `REL-019` |
| Upload Key設定ガード | 秘密値の一部設定、相対パス、リポジトリ内ファイル、署名必須フラグなし、Configuration Cache有効化の5異常系を拒否し、例外へ架空の秘密値を含めないことを確認 | 2026-09-04に`:app:verifyUploadSigningGuards`を実行 | `REL-023` |
| 署名済みAAB | Upload KeyのSHA-256が成果物とPlay Consoleで一致 | [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md) | `REL-024` |
| Release成果物改変検出 | 正常系1件とSBOMの内容・パス・欠損・容量・SHA-256・必須component・依存グラフの異常系6件が成功 | 2026-09-04に`node --test tools/release/release-artifact-verifier.test.mjs`を実行 | `REL-026` |
| 論理日・繰り返し計算 | 試験IDを接頭辞に持つ専用JUnitテスト26件が成功し、ID・テストメソッド・実行タスクの1対1対応を機械検証 | 2026-09-04に`:app:testDebugUnitTest --tests com.mochisofts.mata.domain.model.ScheduleTestSpecCoverageTest`と`verify-automated-evidence.mjs`を実行 | `DAY-001`〜`DAY-003`、`DAY-006`〜`DAY-008`、`DAY-013`、`RPT-001`〜`RPT-004`、`RPT-007`〜`RPT-018`、`RPT-027`〜`RPT-029` |

上表の論理日・繰り返し計算26件は[自動試験証跡TSV](automated-test-evidence.tsv)で1対1に関連付ける。それ以外の自動テスト名と試験IDは現状1対1で機械的に関連付けられていないため、対応領域の証跡として利用しても、関連する全項目を自動的に合格扱いにはしない。

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

端末・OS・日時を特定できない既存の「問題なし」は、関連領域の`一部確認`として扱う。

## 5. リリース項目の現在位置

| 状態 | 試験ID | 根拠または残作業 |
| --- | --- | --- |
| 証跡あり | `REL-001`、`REL-005`〜`REL-007`、`REL-018`〜`REL-020`、`REL-023`〜`REL-027` | 第3節の自動検査および署名証明書照合 |
| 一部確認 | `REL-002`〜`REL-004`、`REL-009`、`REL-011`〜`REL-017`、`REL-021`、`REL-022` | テストや成果物は存在するが、項目が要求するレビュー・実機条件・外部突合・commit対応の一部が未記録 |
| 未完了 | `REL-008`、`REL-010` | 全テーマ・最大フォントと、全P0/P1の集計を項目どおりに確認する |

`REL-010`は他の全P0/P1が揃った時点で最後に判定する。

## 6. Closed testing中の確認順

1. テスターごとに端末名、Androidバージョン、アプリversion、確認日、担当した試験IDを記録する。
2. まずP0の基本フロー、データ整合性、論理日・繰り返し、通知、ウィジェット、バックアップを確認する。
3. API 26、33、36、スマートフォン、タブレット、分割画面、ライト・ダーク、最大フォント、TalkBackの不足枠を埋める。
4. 広告を表示可能な状態になった後、UMP同意、バナー配置、読込失敗、オフライン、ウィジェット操作画面の広告を確認する。本番広告はクリックしない。
5. versionCode `2`以上を配信する最初の機会に、Google Play経由の上書き更新と既存データ・通知・ウィジェットの維持を確認する。
6. Pre-launch report、SDK Index、権限、Data safety、ポリシー警告を最終確認する。
7. 全P0/P1の結果を集計し、`REL-010`と本番公開判定を更新する。

## 7. 証跡の記録形式

Closed testingの各実施記録には、最低限、次を含める。

| 項目 | 記入内容 |
| --- | --- |
| 試験ID | 例: `WGT-005` |
| アプリ | versionName、versionCode、可能ならソースcommit |
| 環境 | 端末名、Androidバージョン、画面状態、テーマ、文字サイズ、権限、ネットワーク |
| 実施 | 日時、実施者、事前データ、操作 |
| 結果 | `合格`、`不合格`、`保留`、`対象外` |
| 証跡 | スクリーンショット、画面録画、ログ、Issue番号 |

同じビルド・端末・条件で連続実施した項目は、環境情報を共通化して複数IDをまとめて記録してよい。
