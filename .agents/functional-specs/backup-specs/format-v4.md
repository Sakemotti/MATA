# MATA 手動バックアップ形式バージョン4

- 文書状態: 確定
- 最終更新日: 2026-09-08
- 基底仕様: [バックアップ形式バージョン3](format-v3.md)

## 1. 適用範囲

バージョン4は、単発TODOの期限日、未完了繰り越し、実行記録の元の実行日と解決日を追加した現行書き出し形式である。バージョン3のZIP構造、共通終了時刻、繰り返し設定、正規順序、件数、ハッシュ、入力上限および参照整合性を継承する。

- `manifest.json.formatVersion`: `4`
- `manifest.json.minimumReaderVersion`: `4`
- `data.json.formatVersion`: `4`
- 読み込み側はバージョン1～4を受け付け、旧形式を1段階ずつ現行モデルへ変換する。

## 2. todosの変更

各TODO要素へ次を追加する。フィールド順は`endDate`の後に`dueDate`、`deadlineMinute`の後に`carryOverEnabled`とする。

| フィールド | 型 | 制約・意味 |
| --- | --- | --- |
| dueDate | string/null | 単発TODOだけに設定可能なISO日付。指定時は`startDate`以降、繰り返しTODOではnull |
| carryOverEnabled | boolean | 日付固定型TODOの未完了繰り越し。回数指定TODOではfalse |

`dueDate=null`は単発TODOの有効期限日を`startDate`と同日として扱う。期限日と繰り越しの意味は[実行期間・期限日・未完了繰り越し仕様](../domain-specs/execution-window-and-carryover.md)に従う。

## 3. executionsの変更

各実行記録へ次を追加する。フィールド順は`logicalDate`、`scheduledLogicalDate`、`resolvedLogicalDate`とする。

| フィールド | 型 | 制約・意味 |
| --- | --- | --- |
| scheduledLogicalDate | string | 元の実行日となるISO日付 |
| resolvedLogicalDate | string/null | `completed`と`skipped`では操作時の論理日、`missed`ではnull |

- `completed`と`skipped`では`logicalDate`と`resolvedLogicalDate`が一致しなければならない。
- `missed`では`logicalDate`を有効期限日とし、`resolvedLogicalDate`をnullとする。
- `scheduledLogicalDate`はTODOの定義と履歴スナップショットに対して妥当な実行日でなければならない。

## 4. runtimeStatesの変更

各実行状態へ`reconciliationCursorDate`の後に次を追加する。

| フィールド | 型 | 制約・意味 |
| --- | --- | --- |
| pendingScheduledLogicalDate | string/null | 未完了繰り越し中の最古の実行日。対象TODOが繰り越し可能で、未解決記録がある場合だけ値を持つ |

復元時はこの値を無条件に信用せず、TODO定義、確定履歴および復元時点から再検証する。不整合な保留状態だけを黙って破棄せず、意味検証で復元を拒否する。

## 5. 履歴スナップショット

現行スナップショットには次を追加する。

- 期限日またはnull
- 未完了繰り越し設定
- 元の実行日
- 解決日またはnull
- 履歴の表示・集計日

旧形式の確定済みスナップショット自体は書き換えず、読み込み時の表示モデル変換で不足値を補う。

## 6. 旧形式からの移行

- バージョン1～3のTODOは`dueDate=null`、`carryOverEnabled=false`として復元する。
- 旧形式の実行記録では`scheduledLogicalDate=logicalDate`とする。
- 旧形式の`completed`と`skipped`では`resolvedLogicalDate=logicalDate`、`missed`では`resolvedLogicalDate=null`とする。
- 旧形式の実行状態では`pendingScheduledLogicalDate=null`とする。
- 既存の期限時刻、履歴、通知、カテゴリ参照、TODO IDおよび定義リビジョンを変更しない。

## 7. 受け入れ条件

1. バージョン4で期限日と未完了繰り越し設定を欠落なく往復できる。
2. 元の実行日、解決日、履歴表示日を状態に応じた制約付きで往復できる。
3. 繰り越し中の最古の実行日を復元前検証し、復元後に一覧、通知、ウィジェットを再構築できる。
4. バージョン1～3を期限日未設定、繰り越しオフとして従来動作を維持して復元できる。
5. 不正な期限日、回数指定TODOの繰り越し、矛盾する履歴日付、無効な保留状態をデータ変更前に拒否できる。
6. バージョン4をバージョン3までにしか対応しない読込実装へ受け入れさせない。
