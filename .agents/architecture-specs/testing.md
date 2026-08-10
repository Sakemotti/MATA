# アーキテクチャ検証仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 親仕様: [実装アーキテクチャ・データアクセス仕様](README.md)

## 1. 静的検査

- domainからAndroid、Compose、Room、DataStore、SDKへの依存がないことを検査する。
- UIからDAO、DataStore、SDKを直接参照しないことを検査する。
- 依存関係の循環と禁止package参照を検出する。
- Navigation引数にEntity、ユーザー入力、購入トークンがないことをレビューする。
- ComposableからRepositoryまたは長時間suspend処理を直接呼ばないことを確認する。

## 2. 単体試験

- UseCaseは固定Clock、ZoneId、ID生成器、Fake Repositoryで全業務分岐を試験する。
- ViewModelはFake UseCaseとRepository FlowでUiStateとUiEffectの遷移を試験する。
- UiEffectの処理済み通知、Activity再生成、二重操作を試験する。
- AdapterはSDK結果、例外、取消、古いコールバックを共通型へ変換できることを試験する。
- DispatcherをTestDispatcherへ置換し、時間依存処理を実時間待機なしで試験する。

## 3. Room・DataStore試験

- DAOのProjection、並び順、外部キー、一意制約、トランザクションをinstrumented testで確認する。
- 日本語Collation Keyの同値時順序と再生成を確認する。
- NFKC、全角・半角、英大小、空白、SQLワイルドカードの検索を確認する。
- Pagingの初期50件、追加50件、末尾、再試行、invalidate、重複なしを確認する。
- 月集計、選択日明細、隣接月先読み、3か月キャッシュ、古い結果破棄を確認する。
- DataStoreの既定値、複数値更新、破損時の安全なエラーを確認する。
- 公開済み全Room schemaからMigration testを行う。

## 4. Hiltと結合試験

- Hilt testでRepository、Clock、Dispatcher、SDK AdapterをFakeへ置換する。
- DebugとReleaseで意図したBindingだけが解決されることを確認する。
- WorkerとReceiverが同じRepository・Schedulerを利用し、別のDBを生成しないことを確認する。
- Activity再生成とプロセス再生成でViewModel、SavedState、永続処理へ正しく再接続する。

## 5. ジョブ試験

- 500件チャンクの境界前後、途中終了、再開、重複実行を確認する。
- 通知再整合WorkのUnique名、KEEP、30秒バックオフ、10回上限、次契機での再登録を確認する。
- DB確定後にOS連携が失敗しても、TODOと履歴が巻き戻らず未整合状態から回復できる。
- 同じ通知、ウィジェット、画面操作が同時に到着しても1回だけ状態変更する。

## 6. 受け入れ条件

1. UI、domain、data、OS・SDK Adapterの依存方向を守れる。
2. RoomとDataStoreをSingle Source of Truthとして全画面が同じ状態を参照できる。
3. Hiltで本番依存とFakeを型安全に差し替えられる。
4. ViewModelが不変UiStateを公開し、型付きActionとEffectを一方向に処理できる。
5. Activity再生成でEffectを重複実行せず、未処理Effectと下書きを復元できる。
6. 型付きNavigationへIDと最小値だけを渡し、不正引数を安全に処理できる。
7. アーカイブと履歴を50件ずつ取得し、検索を300ms debounceできる。
8. 日本語タイトルを固定Collation Keyで安定して並べられる。
9. カレンダーを対象月と隣接月だけ取得し、高速切替の古い結果を破棄できる。
10. 長期整合を500件ずつ確定し、中断位置から重複なく再開できる。
11. OS通知の取消失敗をUnique Workで再整合し、データを削除しない。
12. 全公開DBスキーマから最新へデータを保持して移行できる。
