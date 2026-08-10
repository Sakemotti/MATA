# MATA バックアップ形式バージョン1

- 文書状態: 初版
- 最終更新日: 2026-08-10
- 形式ID: `com.mochisofts.mata.backup`
- 形式バージョン: `1`

## 1. 目的

MATAの手動バックアップを、将来のRoomスキーマ変更やアプリ更新後も検証、移行、復元できる安定した交換形式として定義する。

本形式はアプリ内部のデータベースファイル、Roomエンティティの直列化結果、DataStoreファイルの複製ではない。保存層の値を公開バックアップモデルへ変換して出力する。

## 2. ファイル識別

| 項目 | 値 |
| --- | --- |
| 拡張子 | `.mata-backup` |
| コンテナ | ZIP |
| MIMEタイプ | `application/zip` |
| 形式ID | `com.mochisofts.mata.backup` |
| 初期形式バージョン | `1` |
| 文字コード | UTF-8、BOMなし |
| 圧縮方式 | DEFLATE |

- 拡張子とMIMEタイプはファイル選択時のヒントとしてのみ使用する。
- MATAのバックアップであることはZIP構造と`manifest.json`の形式ID、形式バージョンで判定する。
- ファイル内容をアプリ独自には暗号化しない。
- SHA-256は転送失敗や破損を検出するための値であり、作成者の真正性や改ざん耐性を保証しない。

## 3. ZIP構造

バージョン1は次の2エントリだけを、この順で格納する。

```text
data.json
manifest.json
```

- ディレクトリは作成しない。
- `data.json`を先に書き、非圧縮データのSHA-256、バイト数、件数を計算してから`manifest.json`を書く。
- 両エントリのZIPタイムスタンプには同じバックアップ作成時点を設定する。
- ZIPコメントは設定しない。
- エントリ名は上記との完全一致とする。
- 重複エントリ、未知の追加エントリ、ディレクトリエントリ、空の名前、絶対パス、`..`、`/`または`\`を含む名前は拒否する。
- ZIPのCRCと`manifest.json`に記録したSHA-256の両方を検証する。

## 4. JSON共通規則

- JSONオブジェクトのフィールド名は固定のcamelCaseとする。
- 形式バージョン1で定義していないフィールドは出力せず、読み込み時にも拒否する。
- 同一オブジェクト内の重複キーを拒否する。
- 省略可能と明記した値もフィールド自体は省略せず、値がない場合は`null`を出力する。
- IDはハイフン付きの小文字canonical UUID文字列とする。
- 日付と論理日は`yyyy-MM-dd`のISO-8601文字列とする。
- 時点はUTCのEpochミリ秒をJSON整数で表す。
- 期限時刻は0:00からの経過分を0～1439の整数で表す。
- 一日の終了時刻は0～23の整数で表す。
- 列挙値には本仕様または参照先仕様で定めた小文字の安定コードを使用する。
- JSON整数へ小数、指数表記、`NaN`、無限大を使用しない。
- Room内でJSON文字列として保持する繰り返しパラメータと履歴スナップショットは、解析済みのJSONオブジェクトとして格納する。JSON文字列の二重エンコードは禁止する。

## 5. manifest.json

### 5.1 ルート構造

`manifest.json`は次のフィールドをこの順で持つ。

| フィールド | 型 | バージョン1の値・意味 |
| --- | --- | --- |
| formatId | string | `com.mochisofts.mata.backup` |
| formatVersion | integer | `1` |
| minimumReaderVersion | integer | `1` |
| backupId | string | バックアップごとに生成するUUID |
| createdAt | integer | バックアップの整合スナップショットを確定したUTC Epochミリ秒 |
| appVersionName | string | 作成アプリのバージョン名 |
| appVersionCode | integer | 作成アプリのバージョンコード |
| roomSchemaVersion | integer | 作成時のRoomスキーマバージョン。診断用であり復元可否の直接条件にしない |
| data | object | `data.json`の検証情報 |
| counts | object | データ種別ごとの件数 |

`data`は次を持つ。

| フィールド | 型 | 意味 |
| --- | --- | --- |
| sha256 | string | 非圧縮`data.json`のSHA-256、小文字64桁16進数 |
| uncompressedBytes | integer | UTF-8で符号化した非圧縮`data.json`のバイト数 |

`counts`は次を持つ。

| フィールド | 対応配列 |
| --- | --- |
| categories | `categories` |
| todos | `todos` |
| notifications | `notifications` |
| executions | `executions` |
| periodResults | `periodResults` |
| runtimeStates | `runtimeStates` |

各件数は0以上の整数とし、`data.json`内の実件数と完全に一致しなければならない。

### 5.2 minimumReaderVersion

- 読み込み側は`formatVersion`を解釈でき、かつ`minimumReaderVersion`以上の読み込み実装を持つ場合だけ復元できる。
- バージョン1では両方とも1とする。
- 将来、古い読み込み側が未知フィールドを無視するとデータの意味を失う変更を行う場合は`minimumReaderVersion`を引き上げる。

## 6. data.jsonルート

`data.json`は次のフィールドをこの順で持つ。

| フィールド | 型 | 意味 |
| --- | --- | --- |
| formatVersion | integer | `1` |
| settings | object | バックアップ対象の一般設定 |
| categories | array | ユーザーカテゴリ |
| todos | array | 通常・アーカイブ済みTODO |
| notifications | array | TODOごとの通知設定 |
| executions | array | 完了、スキップ、確定済み未完了の実行記録 |
| periodResults | array | 週X回・月X回の確定済み期間結果 |
| runtimeStates | array | TODOごとの整合処理位置 |

## 7. settings

| フィールド | 型 | 値 |
| --- | --- | --- |
| uncategorizedEndHour | integer | カテゴリ未設定TODOの一日の終了時刻、0～23 |
| weekStartDay | string | `monday`、`tuesday`、`wednesday`、`thursday`、`friday`、`saturday`、`sunday` |
| showCompletedTodos | boolean | 完了済みTODOの表示有無 |
| theme | string | `system`、`light`、`dark` |

- 将来バックアップ対象の設定を追加する場合は、バックアップ形式バージョンを更新する。
- Google Playの購入状態、通知権限、正確なアラーム権限、ウィジェット設定は含めない。

## 8. categories

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| id | string | UUID |
| name | string | 1～30文字 |
| normalizedName | string | 名前から仕様どおり再計算した値と一致 |
| colorIndex | integer | 0～15 |
| iconKey | string | 対応するMaterial Icon識別子 |
| sortOrder | integer | 0始まりの重複しない連番 |
| endHour | integer | 0～23 |
| createdAt | integer | UTC Epochミリ秒 |
| updatedAt | integer | UTC Epochミリ秒、`createdAt`以上 |

「カテゴリ未設定」は配列へ含めない。TODOの`categoryId`が`null`の場合に表現する。

## 9. todos

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| id | string | UUID |
| title | string | 1～100文字 |
| description | string | 0～1000文字 |
| categoryId | string/null | 存在するカテゴリIDまたは`null` |
| startDate | string | ISO日付 |
| endDate | string/null | `null`は無期限、指定時は`startDate`以降 |
| repeatType | string | 繰り返し方式の安定コード |
| repeatParamsVersion | integer | 対応するパラメータ形式バージョン |
| repeatParams | object | 方式固有パラメータ |
| deadlineMinute | integer/null | 0～1439、`null`は期限設定なし |
| definitionRevision | integer | 1以上 |
| archivedAt | integer/null | アーカイブ時点 |
| createdAt | integer | UTC Epochミリ秒 |
| updatedAt | integer | UTC Epochミリ秒、`createdAt`以上 |

`repeatType`とバージョン1の`repeatParams`は次の組み合わせとする。

| repeatType | repeatParams |
| --- | --- |
| `none` | `{}` |
| `daily` | `{}` |
| `weekdays` | `{}` |
| `selected_weekdays` | `{ "weekdays": [曜日コード...] }` |
| `monthly_day` | `{ "day": 1～31 }` |
| `month_end` | `{}` |
| `every_n_days` | `{ "intervalDays": 1以上 }` |
| `weekly_count` | `{ "requiredCount": 1～7 }` |
| `monthly_count` | `{ "requiredCount": 1～31 }` |

曜日コードは`monday`から`sunday`までとし、重複を許可しない。`selected_weekdays`では月曜日から日曜日の順に格納する。

## 10. notifications

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| id | string | UUID |
| todoId | string | 存在するTODO ID |
| relation | string | `before`、`at`、`after` |
| amount | integer | `at`は0、その他は1～999 |
| unit | string/null | `at`は`null`、その他は`minute`、`hour`、`day` |
| sortOrder | integer | TODO内で0始まりの重複しない連番 |
| createdAt | integer | UTC Epochミリ秒 |
| updatedAt | integer | UTC Epochミリ秒、`createdAt`以上 |

- 1つのTODOにつき最大10件とする。
- 同じTODO内で同一通知タイミングを重複させない。
- 通知が現在の期限や終了時刻に対して発火不能でも、保存済み設定として構造上有効なら復元対象に含める。復元後の再検証で発火対象外として扱う。

## 11. executions

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| id | string | UUID |
| operationId | string | UUID、全実行記録で一意 |
| todoId | string | 存在するTODO ID |
| logicalDate | string | ISO日付 |
| status | string | `completed`、`skipped`、`missed` |
| actedAt | integer/null | `completed`と`skipped`では必須、`missed`では`null` |
| finalizedAt | integer | UTC Epochミリ秒 |
| definitionRevision | integer | 1以上、TODOの現在リビジョン以下 |
| snapshotVersion | integer | 対応する履歴スナップショット形式 |
| snapshot | object | 当時の完全な表示・計算情報 |

`todoId`と`logicalDate`の組を一意とする。`snapshot`は[データモデル仕様](../domain-specs/data-model.md)の履歴スナップショット項目を持ち、`snapshotVersion`に対応するスキーマで検証する。

## 12. periodResults

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| id | string | UUID |
| todoId | string | 存在するTODO ID |
| periodType | string | `weekly_count`または`monthly_count` |
| periodStart | string | ISO日付 |
| periodEnd | string | ISO日付、`periodStart`以降 |
| requiredCount | integer | weeklyは1～7、monthlyは1～31 |
| completedCount | integer | 0以上 |
| achieved | boolean | `completedCount >= requiredCount`の結果と一致 |
| displayDate | string | `periodStart`から`periodEnd`の範囲内 |
| finalizedAt | integer | UTC Epochミリ秒 |
| definitionRevision | integer | 1以上、TODOの現在リビジョン以下 |
| snapshotVersion | integer | 対応する履歴スナップショット形式 |
| snapshot | object | 当時のTODO、カテゴリ、期間境界など |

`todoId`、`periodStart`、`periodEnd`の組を一意とする。期間境界、必要回数、表示日はスナップショットの内容とも一致しなければならない。

## 13. runtimeStates

各要素は次を持つ。

| フィールド | 型 | 制約 |
| --- | --- | --- |
| todoId | string | 存在するTODO ID、配列内で一意 |
| lastFinalizedLogicalDate | string/null | ISO日付 |
| lastFinalizedWeeklyPeriodEnd | string/null | ISO日付 |
| lastFinalizedMonthlyPeriodEnd | string/null | ISO日付 |
| appliedDefinitionRevision | integer | 1以上、TODOの現在リビジョン以下 |
| reconciliationCursorDate | string/null | ISO日付 |
| updatedAt | integer | UTC Epochミリ秒 |

復元後は値を無条件に信用せず、確定履歴と現在環境に対して再検証する。不整合があれば履歴を失わない安全な位置から整合処理を再開する。

## 14. 正規順序

同じ入力データは常に同じ`data.json`を生成する。バックアップファイル全体では、manifestのバックアップ作成日時と`backupId`だけが作成ごとに変化する。

- JSONオブジェクトのフィールド順は本仕様の表の順とする。
- `categories`: `sortOrder`、`id`の昇順。
- `todos`: `createdAt`、`id`の昇順。
- `notifications`: `todoId`、`sortOrder`、`id`の昇順。
- `executions`: `logicalDate`、`todoId`、`id`の昇順。
- `periodResults`: `periodStart`、`todoId`、`id`の昇順。
- `runtimeStates`: `todoId`の昇順。
- `snapshot`内の配列も各スナップショットバージョンで定めた順序に正規化する。
- JSONライターは不要な空白を出力しない。

正規順序は再現可能性とテストのための規則であり、SHA-256による真正性を与えるものではない。

## 15. バックアップ対象

次を含める。

- 通常およびアーカイブ済みのTODO
- TODOごとの複数通知設定
- ユーザーカテゴリ、色、アイコン、並び順、終了時刻
- 完了、スキップ、確定済み未完了の実行履歴
- 週X回・月X回の確定済み期間結果
- 履歴の表示・計算用スナップショット
- TODOごとの整合処理位置
- 共通終了時刻、週の開始曜日、完了済み表示、テーマ
- 将来の形式バージョンで明示的にバックアップ対象へ追加された設定

## 16. バックアップ対象外

次を含めない。

- Google Playの広告削除購入状態
- 祝日キャッシュと祝日取得状態
- OSへ登録済みのアラーム、`ScheduledNotificationEntity`、通知ID、配信済み通知の内部状態
- Androidの通知権限と正確なアラーム権限
- ウィジェットの配置、サイズ、一時状態、表示スナップショット
- 画面の選択状態、スクロール位置、ナビゲーション状態
- 未保存のTODOまたはカテゴリ下書き
- 一時ファイル、ログ、キャッシュ、バックアップ・復元処理の一時状態

## 17. 互換性

- バックアップ形式バージョンはRoomスキーマバージョンと独立して管理する。
- 公開済みバージョンのフィールドの意味、単位、列挙コードを後から変更しない。
- フィールド追加、削除、意味変更が必要な場合は`formatVersion`を増やす。
- 対応する旧形式は1バージョンずつ段階的に現在形式へ変換し、各段階で検証する。
- 未対応の新しい`formatVersion`または`minimumReaderVersion`は推測して読み込まず、復元を拒否する。
- 形式ごとにスキーマ相当の定義、移行処理、ゴールデンファイルをリポジトリへ保持する。

## 18. 受け入れ条件

1. バージョン1のファイルをZIP構造、manifest、dataの3段階で識別できる。
2. `data.json`の非圧縮バイト列から同じSHA-256を再計算できる。
3. manifestの件数とdataの全配列件数が一致する。
4. RoomとDataStoreの物理形式を公開ファイルへ直接格納しない。
5. JSON文字列として保存された複合値を二重エンコードしない。
6. 同一スナップショットから安定した順序の`data.json`を生成できる。
7. バックアップ対象と対象外をデータ種別ごとに明確に判定できる。
8. 未対応の形式を部分的に読み込まず拒否できる。
