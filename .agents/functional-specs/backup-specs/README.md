# MATA 手動バックアップ仕様

- 文書状態: 初版
- 最終更新日: 2026-08-10

このフォルダでは、MATAの手動バックアップ作成、バックアップファイル形式、復元、互換性、安全対策、テストを定義する。

## 文書一覧

- [バックアップ形式バージョン3](format-v3.md)
  - 現行形式。一日の終了時刻を全TODO共通へ統一
- [バックアップ形式バージョン2](format-v2.md)
  - 旧形式。N週間にX回の期間週数と対象日条件、曜日プリセットを追加
- [バックアップ形式バージョン1](format-v1.md)
  - 旧形式。`.mata-backup`の基本ZIP構造、`manifest.json`、`data.json`、型、並び順
- [バックアップ作成仕様](create-backup.md)
  - Storage Access Framework、整合スナップショット、ストリーミング出力、長時間処理、失敗時の回復
- [バックアップ復元仕様](restore-backup.md)
  - ファイル選択、事前検証、全置換、ロールバック、復元後の再構築
- [安全性・テスト仕様](security-and-testing.md)
  - 入力上限、ZIP・JSON対策、プライバシー、互換性テスト、障害注入

## 関連文書

- [アプリ全体仕様](../../app-spec.md)
- [開発ガイドライン](../../development-guidelines.md)
- [データモデル仕様](../domain-specs/data-model.md)
- [繰り返し計算仕様](../domain-specs/recurrence-rules.md)
- [状態遷移・整合処理仕様](../domain-specs/state-transitions.md)
- [通知仕様](../notification-specs/README.md)
- [ウィジェット仕様](../widget-specs/README.md)
- [設定画面仕様](../../screen-specs/settings/spec.md)

## 基本原則

1. バックアップと復元はユーザー操作による完全バックアップだけとし、自動、差分、クラウド同期は提供しない。
2. 復元は現在データとのマージを行わず、対象データをすべて置き換える。
3. バックアップ形式はRoomの内部テーブルやDataStoreの物理表現から独立した公開形式とする。
4. 復元ファイルを信頼せず、構造、ハッシュ、型、値、参照関係を現在データの変更前に検証する。
5. 作成と復元はストリーミング処理とし、データ全体をメモリへ読み込まない。
6. 復元の結果は、復元前または復元完了のどちらか一方にし、部分復元をユーザーへ公開しない。
7. バックアップファイルをアプリ独自には暗号化しない。SHA-256は破損検知に使用し、真正性や機密性を保証しない。
8. UMPの同意状態など、外部サービスまたは再生成可能な内部状態はバックアップへ含めない。

## Android公式資料

- [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)
- [Persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Support for long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
