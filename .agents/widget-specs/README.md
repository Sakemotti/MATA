# MATA ウィジェット仕様

- 文書状態: 初版
- 最終更新日: 2026-08-10

このフォルダでは、ホーム画面へ表示する「今日のTODO」ウィジェットのレイアウト、表示データ、操作、更新、テストを定義する。

## 文書一覧

- [レイアウト・表示データ仕様](layout-and-data.md)
  - Glance、サイズ、カテゴリ表示、TODO行、論理日、読み込み、エラー、アクセシビリティ
- [ウィジェット操作仕様](interactions.md)
  - アプリを開く、完了、元に戻す、重複防止、インスタンス状態
- [更新・テスト仕様](updates-and-testing.md)
  - 更新契機、時刻境界、WorkManager、再起動、プレビュー、性能、テスト

## 関連文書

- [アプリ全体仕様](../app-spec.md)
- [データモデル仕様](../domain-specs/data-model.md)
- [繰り返し計算仕様](../domain-specs/recurrence-rules.md)
- [状態遷移・整合処理仕様](../domain-specs/state-transitions.md)
- [日本の祝日データ仕様](../holiday-specs/README.md)
- [通知仕様](../notification-specs/README.md)
- [アプリ起動・初期化・復帰仕様](../startup-specs/README.md)
- [TODO一覧画面仕様](../screen-specs/todo-list/spec.md)

## 基本原則

1. 今日実行すべき未完了TODOを、カテゴリごとの論理日に従って全件表示する。
2. TODO一覧、通知と同じRepositoryとドメイン計算エンジンを使用する。
3. 完了以外の編集操作はアプリで行う。
4. 複数インスタンスは同じTODOデータを表示し、サイズと一時状態だけを個別管理する。
5. ウィジェット更新のためだけに正確なアラームやバッテリー最適化除外を要求しない。
6. Glance専用Composableを使用し、通常のCompose UIを混在させない。

## Android公式資料

- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
- [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui)
- [Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- [Handle user interaction](https://developer.android.com/develop/ui/compose/glance/user-interaction)
