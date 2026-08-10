# MATA 通知仕様

- 文書状態: 初版
- 最終更新日: 2026-08-10

このフォルダでは、MATAの通知候補計算、Androidへの登録、通知表示、通知からの操作、再登録と障害復旧を定義する。

## 文書一覧

- [通知スケジュール仕様](scheduling-rules.md)
  - 通知タイミング、次回候補、AlarmManager、正確・通常アラーム、内部登録情報
- [通知表示・操作仕様](display-and-actions.md)
  - 権限、チャンネル、通知内容、グループ、完了、元に戻す、アプリ起動
- [再登録・テスト仕様](reconciliation-and-testing.md)
  - 再起動、時刻変更、編集、WorkManager、障害復旧、診断、テスト

## 関連文書

- [アプリ全体仕様](../../app-spec.md)
- [データモデル仕様](../domain-specs/data-model.md)
- [繰り返し計算仕様](../domain-specs/recurrence-rules.md)
- [状態遷移・整合処理仕様](../domain-specs/state-transitions.md)
- [日本の祝日データ仕様](../holiday-specs/README.md)
- [アプリ起動・初期化・復帰仕様](../../non-functional-specs/startup-specs/README.md)
- [TODO編集画面仕様](../../screen-specs/todo-editor/spec.md)
- [設定画面仕様](../../screen-specs/settings/spec.md)

## 基本原則

1. 通知はドメイン計算エンジンが返した期限と実行対象を使用する。
2. AlarmManager内で繰り返し条件を再実装しない。
3. 経過済み通知を後から発火させない。
4. 通知表示前と通知操作時に最新状態を再検証する。
5. 通知設定は権限がなくても保持する。
6. Androidの権限、チャンネル、音、振動、Do Not Disturbを正とする。
7. 通知の完了操作は画面、ウィジェットと同じRepository処理を使用する。

## Android公式資料

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)
