# UI状態・Navigation仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 親仕様: [実装アーキテクチャ・データアクセス仕様](README.md)

## 1. UiState

- 画面ごとに単一の不変`UiState`をViewModelから`StateFlow`で公開する。
- 初期値を持たせ、Composableがnull状態を推測しないようにする。
- 読み込み、内容、空、再取得、送信中、回復可能エラー、遮断エラーを型または明示プロパティで区別する。
- UiStateには描画に必要な表示モデルだけを含め、Room Entity、Context、NavController、SDK型を含めない。
- 永続データのコピーをViewModel内だけで正として保持せず、RepositoryのFlowへ操作中状態を合成する。
- `collectAsStateWithLifecycle`でライフサイクル対応購読する。

## 2. イベント

- Composableはユーザー操作を型付き`UiAction`としてViewModelへ渡す。
- ViewModelの公開メソッドを画面操作単位にし、ComposableからRepositoryを呼ばない。
- 保存、削除、完了などの二重操作をViewModelとRepositoryの両方で防ぐ。
- Snackbar、Navigation、外部画面起動など一度だけの効果は、ID付き`UiEffect`として保持し、UIが処理済みIDを通知する。
- Activity再生成で同じEffectを重複実行せず、未処理Effectは復元する。
- プロセス再生成後に永続処理の結果をRepositoryから再取得し、古いメモリイベントを再生しない。

## 3. 入力下書き

- TODOとカテゴリの編集中の値はViewModelのUiStateへ保持する。
- 保存済みEntityと編集中のDraft modelを分ける。
- 文字入力のたびにRoomへ保存しない。
- `SavedStateHandle`には100文字のタイトル、1000文字の説明、選択値など復元に必要な小さい下書きだけを保存する。
- 大容量の履歴、一覧、バックアップ内容をSavedStateへ格納しない。
- 別画面でカテゴリを作成して戻る場合も同じDraftを復元する。

## 4. Navigation

- Navigation Composeの型付きルートを使用し、文字列連結でrouteを構築しない。
- ルート引数には画面ID、TODO ID、カテゴリID、ISO日付など最小値だけを渡す。
- TODOタイトル、説明、Entity、履歴本文、購入トークンを引数へ含めない。
- 詳細画面はIDからRepositoryの最新値を取得する。
- 必須引数の欠落、不正形式、対象消失では安全な一覧へ戻し、クラッシュしない。
- 起動元がランチャー、通知、ウィジェットでも同じ型付きDestinationへ正規化する。

## 5. Back stack

- 主要画面の切替は同じDestinationを多重に積まず、既存仕様の選択状態を維持する。
- 編集保存後は編集画面をpopし、一覧を新規に重複生成しない。
- 2ペインと単一ペインの変化はDestinationではなくレイアウト状態として扱う。
- モーダルドロワー、ダイアログ、ボトムシートが開いている場合は戻る操作で先に閉じる。
- Predictive Backへ対応し、確定前の破棄確認を既存画面仕様どおり表示する。

## 6. SavedState

次を`SavedStateHandle`または`rememberSaveable`へ用途に応じて保存する。

- TODO一覧の選択日とフィルター、カテゴリ別TODO一覧の選択カテゴリ
- カレンダーの表示月、選択日
- アーカイブ検索、並び順、選択ID
- 編集Draft、フォーカス対象、開いている選択UI
- Snackbarの元に戻す対象と期限
- 外部Activity Resultの待機状態と処理ID

- LazyListの画面内スクロールは`rememberSaveable`を基本とする。
- Repositoryから復元できる一覧内容をBundleへ保存しない。
- 新規表示時に復元しない状態は各画面仕様に従って初期化する。

## 7. 時刻変化

- 現在時刻は注入したClockから取得し、Composableの再コンポーズを時刻源にしない。
- ViewModelは画面表示中に必要な次の論理日境界または期限だけをSchedulerへ登録する。
- 時刻、日付、タイムゾーン変更通知を受けた場合はRepository整合後の状態を購読する。
- 過去履歴のスナップショットは現在設定から再計算しない。
