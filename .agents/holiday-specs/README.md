# MATA 日本の祝日データ仕様

- 文書状態: 初版
- 最終更新日: 2026-08-10

このフォルダでは、日本の祝日データの取得、検証、キャッシュ、更新、平日のみTODOへの反映、障害復旧、テストを定義する。

## 文書一覧

- [取得・キャッシュ仕様](fetch-and-cache.md)
  - Holidays JP API、通信、取得契機、再試行、応答検証、Roomモデル、キャッシュ状態
- [連携・再計算仕様](integration-and-recalculation.md)
  - 暫定計算、画面、通知、ウィジェット、更新世代、障害復旧、エラー状態
- [安全性・テスト仕様](security-and-testing.md)
  - プライバシー、ログ、データ移行、単体・統合・外部API契約テスト

## 関連文書

- [アプリ全体仕様](../app-spec.md)
- [データモデル仕様](../domain-specs/data-model.md)
- [繰り返し計算仕様](../domain-specs/recurrence-rules.md)
- [状態遷移・整合処理仕様](../domain-specs/state-transitions.md)
- [通知仕様](../notification-specs/README.md)
- [ウィジェット仕様](../widget-specs/README.md)
- [TODO編集画面仕様](../screen-specs/todo-editor/spec.md)
- [手動バックアップ仕様](../backup-specs/README.md)

## 基本原則

1. 祝日取得をアプリ共通処理へ集約し、画面、通知、ウィジェットから直接通信しない。
2. 祝日を理由に実行日から除外するのは「平日のみ」のTODOだけとする。
3. 取得済みキャッシュを優先し、更新失敗だけを理由に破棄しない。
4. 必要年が未取得でも月曜日から金曜日による暫定計算を許可し、TODO操作を妨げない。
5. 祝日更新は未確定の現在と将来だけへ反映し、確定済み履歴と期間結果を変更しない。
6. ネットワーク応答を信頼せず、レスポンス全体を検証してから3年分を一括更新する。
7. 祝日キャッシュは再取得可能なため、手動バックアップとAndroid自動バックアップへ含めない。

## データソース

- [Holidays JP API](https://holidays-jp.github.io/)
- エンドポイント: `https://holidays-jp.github.io/api/v1/date.json`
- 取得対象: 日本時間における前年、今年、翌年

