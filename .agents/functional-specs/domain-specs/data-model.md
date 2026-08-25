# MATA データモデル仕様

- 文書状態: 初版
- 最終更新日: 2026-08-10
- 対応する決定事項: データ設計質問 1～18、97～126、137～138

## 1. 目的

TODO定義、カテゴリ、通知設定、実行履歴、期間結果、整合処理位置を端末内へ一貫して保存するためのデータ構造を定義する。

将来の実行予定は日付単位で事前生成しない。TODO定義と保存済みの実行記録を入力として、必要な日付範囲だけを動的に計算する。

## 2. 保存基盤

### 2.1 Room

次をRoomのSQLiteデータベースへ保存する。

- TODO定義
- カテゴリ
- TODOごとの通知タイミング
- 完了、スキップ、確定済み未完了の実行記録
- 週X回・月X回の期間結果
- TODOごとの整合処理位置
- 祝日キャッシュと取得状態

RoomはWALを使用する。UIからDAOを直接呼ばず、Repositoryを経由する。

### 2.2 DataStore

次の小規模な設定はDataStoreへ保存する。

- カテゴリ未設定TODOの一日の終了時刻
- 週の開始曜日
- 完了済みTODOの表示有無
- テーマ
- その他、後から追加される小規模なアプリ設定

広告削除購入状態はGoogle Playを正とし、バックアップ対象の一般設定とは分離する。

## 3. 共通データ型

| 種類 | 保存形式 |
| --- | --- |
| ID | UUIDの文字列表現 |
| 列挙値 | 公開後も意味を変えない小文字の文字列コード |
| 日付・論理日 | ISO-8601の yyyy-MM-dd |
| 期限時刻 | 0:00からの経過分、0～1439 |
| 一日の終了時刻 | 時、0～23 |
| 時点 | UTCのEpochミリ秒 |
| 真偽値 | SQLite上の整数としてRoomが変換 |
| 複合パラメータ | スキーマバージョンを持つJSON |

Kotlinのenum ordinalは永続値として使用しない。文字列コードを変更する場合はデータマイグレーションを必須とする。

## 4. カテゴリ未設定

「カテゴリ未設定」はCategoryEntityへ保存しない仮想カテゴリとする。

- TodoEntity.categoryIdがnullの場合にカテゴリ未設定として扱う。
- 名前、色、アイコン、表示順はアプリ内の固定値とする。
- 一日の終了時刻はDataStoreの共通終了時刻を参照する。
- 一覧では常にユーザーカテゴリより前に表示する。
- ユーザーカテゴリ削除時は、所属する通常・アーカイブ済みTODOのcategoryIdをnullへ変更する。

## 5. エンティティ

### 5.1 CategoryEntity

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| id | UUID文字列 | 主キー |
| name | 文字列 | 1～30文字 |
| normalizedName | 文字列 | NFKC、前後空白除去、大文字小文字を正規化 |
| colorIndex | 整数 | 固定16色パレットの0～15 |
| iconKey | 文字列 | 安定したMaterial Icon識別子 |
| sortOrder | 整数 | ユーザーカテゴリ内の0始まり連番 |
| endHour | 整数 | 0～23 |
| createdAt | Epochミリ秒 | 作成時点 |
| updatedAt | Epochミリ秒 | 最終更新時点 |

normalizedNameには一意制約を設ける。並べ替え後は1トランザクションでsortOrderを連番へ振り直す。

### 5.2 TodoEntity

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| id | UUID文字列 | 主キー |
| title | 文字列 | 必須、1～100文字 |
| description | 文字列 | 0～1000文字 |
| categoryId | UUID文字列またはnull | CategoryEntityへの外部キー |
| startDate | ISO日付 | 実行期間の開始日 |
| endDate | ISO日付またはnull | nullは無期限、指定時は開始日以降 |
| repeatType | 文字列コード | 繰り返し方式 |
| repeatParamsVersion | 整数 | パラメータJSONの形式バージョン |
| repeatParamsJson | JSON | 方式固有の値 |
| deadlineMinute | 整数またはnull | 0～1439、nullは論理日の終了時刻 |
| definitionRevision | 整数 | 初期値1、編集成功ごとに増加 |
| archivedAt | Epochミリ秒またはnull | nullは通常、値ありはアーカイブ済み |
| createdAt | Epochミリ秒 | 作成時点 |
| updatedAt | Epochミリ秒 | 最終更新時点 |

終了日がnullであることを無期限の唯一の表現とし、別の無期限フラグは持たない。

repeatTypeには次の安定コードを使用する。

| 方式 | コード |
| --- | --- |
| 繰り返しなし | none |
| 毎日 | daily |
| 平日のみ | weekdays |
| 曜日指定 | selected_weekdays |
| 毎月の指定日 | monthly_day |
| 毎月 第X X曜日 | monthly_nth_weekdays |
| 毎月の月末 | month_end |
| 一定日数ごと | every_n_days |
| N週間にX回 | weekly_count |
| 月X回 | monthly_count |

repeatParamsJsonには、選択した方式に必要な値だけを保存する。

- selected_weekdays: 対象日条件コードと曜日コードの重複しない配列。既存データで対象日条件がない場合は任意曜日として扱う
- monthly_day: 1～31
- monthly_nth_weekdays: 第1～第5と曜日コードの重複しない組み合わせ配列
- every_n_days: 1以上の日数
- weekly_count: 期間週数1～52、必要回数1～期間週数×7、対象日条件、任意曜日。既存データで追加値がない場合は期間週数1・すべての日として扱う
- monthly_count: 1～31
- その他: 空オブジェクト

JSONの読み書きにはKotlin Serializationを使用する。

### 5.3 TodoNotificationEntity

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| id | UUID文字列 | 主キー |
| todoId | UUID文字列 | TodoEntityへの外部キー、削除時CASCADE |
| relation | 文字列コード | before、at、after |
| amount | 非負整数 | atの場合は0 |
| unit | 文字列コード | minute、hour、day |
| sortOrder | 整数 | TODO内の表示順 |
| createdAt | Epochミリ秒 | 作成時点 |
| updatedAt | Epochミリ秒 | 最終更新時点 |

同じTODOへ複数の通知タイミングを保存できる。通知スケジューラが利用するOS側識別子は、TODO ID、通知ID、対象論理日から安定して導出する。

### 5.4 TodoExecutionEntity

完了、スキップ、通常TODOの確定済み未完了を共通のテーブルで管理する。

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| id | UUID文字列 | 主キー |
| operationId | UUID文字列 | 操作の重複防止、一意 |
| todoId | UUID文字列 | TodoEntityへの外部キー、削除時CASCADE |
| logicalDate | ISO日付 | 操作または確定時に割り当てた論理日 |
| status | 文字列コード | completed、skipped、missed |
| actedAt | Epochミリ秒またはnull | 完了・スキップ操作時点 |
| finalizedAt | Epochミリ秒 | 記録が確定した時点 |
| definitionRevision | 整数 | 記録時のTODO定義リビジョン |
| snapshotVersion | 整数 | スナップショット形式 |
| snapshotJson | JSON | 記録時点の完全な表示・計算情報 |

todoIdとlogicalDateの組に一意制約を設ける。同一論理日に完了、スキップ、未完了が重複して存在してはならない。

未操作の現在分はこのテーブルに保存しない。期限超過もstatusとして保存せず、現在時刻から算出する。

### 5.5 PeriodResultEntity

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| id | UUID文字列 | 主キー |
| todoId | UUID文字列 | TodoEntityへの外部キー、削除時CASCADE |
| periodType | 文字列コード | weekly_countまたはmonthly_count |
| periodStart | ISO日付 | 有効な期間開始日 |
| periodEnd | ISO日付 | 有効な期間終了日 |
| requiredCount | 整数 | 確定時の必要回数 |
| completedCount | 整数 | 実際の完了回数。必要回数を超える場合がある |
| achieved | 真偽値 | completedCountがrequiredCount以上 |
| displayDate | ISO日付 | カレンダーへ表示する期間最終有効日 |
| finalizedAt | Epochミリ秒 | 確定時点 |
| definitionRevision | 整数 | 確定時のTODO定義リビジョン |
| snapshotVersion | 整数 | スナップショット形式 |
| snapshotJson | JSON | 当時のTODO、カテゴリ、期間境界など |

todoId、periodStart、periodEndの組に一意制約を設ける。

### 5.6 TodoRuntimeStateEntity

整合処理を再開可能にし、長期未起動後の走査範囲を限定するため、TODOごとに1件保存する。

| 列 | 型 | 制約・意味 |
| --- | --- | --- |
| todoId | UUID文字列 | 主キー、TodoEntityへの外部キー、削除時CASCADE |
| lastFinalizedLogicalDate | ISO日付またはnull | 通常TODOで処理済みの最終論理日 |
| lastFinalizedWeeklyPeriodEnd | ISO日付またはnull | 週X回で処理済みの最終期間末 |
| lastFinalizedMonthlyPeriodEnd | ISO日付またはnull | 月X回で処理済みの最終期間末 |
| appliedDefinitionRevision | 整数 | 最後に整合した定義リビジョン |
| reconciliationCursorDate | ISO日付またはnull | 分割処理中の再開位置 |
| updatedAt | Epochミリ秒 | 最終更新時点 |

このテーブルは手動バックアップへ含める。復元後は現在環境で再検証し、必要に応じて安全な位置から整合処理を再開する。

### 5.7 HolidayEntityとHolidayFetchStateEntity

HolidayEntityは祝日の日付、名称、取得元識別情報を保持する。HolidayFetchStateEntityは年ごとの取得成否、取得日時、データ識別情報を保持する。

祝日キャッシュは再取得可能なため、手動バックアップへ含めない。

列、制約、取得状態、更新トランザクションの詳細は[祝日データ取得・キャッシュ仕様](../holiday-specs/fetch-and-cache.md)に従う。

## 6. 履歴スナップショット

TodoExecutionEntityとPeriodResultEntityのsnapshotJsonには、少なくとも次を含める。

- スナップショット形式バージョン
- TODO IDと定義リビジョン
- タイトル、説明
- 開始日、終了日
- 繰り返し方式と方式固有パラメータ
- 期限時刻
- 通知設定の表示用情報
- カテゴリIDまたはnull
- 当時のカテゴリ名、色、アイコン
- 適用された一日の終了時刻
- 適用された週の開始曜日
- 論理日または期間境界

スナップショット形式の旧バージョンは読み込み時に現在の表示モデルへ変換する。確定済みのJSON自体を後から書き換えない。

## 7. 外部キーと削除

- TODO完全削除では、通知、実行記録、期間結果、実行状態をCASCADEで削除する。
- カテゴリ削除では、同一トランザクション内で通常・アーカイブ済みTODOのcategoryIdをnullにしてからカテゴリを削除する。
- カテゴリ削除によって実行記録と期間結果のスナップショットは変更しない。
- アーカイブはTodoEntity.archivedAtの設定で表現し、別テーブルへ移動しない。
- 自動的な履歴削除や保存期間の上限を設けない。

## 8. インデックス

少なくとも次へインデックスを設ける。

- CategoryEntity.normalizedName
- CategoryEntity.sortOrder
- TodoEntity.categoryId
- TodoEntity.archivedAt
- TodoEntity.startDate、endDate
- TodoExecutionEntity.todoId、logicalDate、status
- PeriodResultEntity.todoId、periodStart、periodEnd、displayDate
- TodoNotificationEntity.todoId、sortOrder

実際のクエリ計画を計測し、必要な複合インデックスを追加する。

## 9. Repository

RepositoryはRoomとDataStoreを統合し、不変の状態をFlowとして公開する。

- UI、通知、ウィジェットはDAOへ直接依存しない。
- 状態変更はRepositoryがトランザクションとして実行する。
- ドメイン計算にはRoomエンティティではなくドメインモデルを渡す。
- DB更新成功後に通知とウィジェットへ更新を伝える。
- 外部処理が失敗しても、確定済みデータの整合性を壊さない。

## 10. 暗号化とバックアップ

- データベースはAndroidのアプリ専用領域へ保存する。
- 初版ではSQLCipherなどのアプリ独自暗号化を導入しない。
- 手動バックアップにはTODO、通知設定、カテゴリ、一般設定、実行記録、期間結果、実行状態を含める。
- 広告削除購入状態、祝日キャッシュ、一時画面状態、未保存下書きは含めない。

## 11. スキーマ管理

- RoomのスキーマJSONをバージョンごとにリポジトリへ保存する。
- destructive migrationを通常のアップデート手段として使用しない。
- すべてのマイグレーションに移行テストを用意する。
- 文字列コード、JSON形式、スナップショット形式にも個別のバージョンを持たせる。
- 制約違反や変換失敗時は既存データを部分更新せず、処理全体を失敗させる。

## 12. 受け入れ条件

1. TODO定義と未操作の予定を重複保存しない。
2. 同一TODO・同一論理日の実行記録を重複登録できない。
3. カテゴリ削除後も過去履歴のカテゴリ表示が変わらない。
4. TODO完全削除で関連するすべてのデータが一貫して削除される。
5. アーカイブと復元で同じTODO IDを維持できる。
6. 履歴を当時のスナップショットだけで表示できる。
7. 長期未起動後の整合処理位置を保存・復元できる。
8. バックアップ対象と対象外をデータ層で区別できる。
9. 将来のスキーマとJSON形式の変更をマイグレーションテストで検証できる。
