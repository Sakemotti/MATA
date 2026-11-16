# Issue #355 前回完了日からN日後の確認項目

- 作成日: 2026-11-13
- 仕様: [解決日基準の繰り返し](../functional-specs/domain-specs/completion-based-recurrence.md)、[バックアップ形式7](../functional-specs/backup-specs/format-v7.md)
- 対象: `feature/repeat-after-completion-355`。公開済みversionCode 11の試験実績とは別に管理する

## 1. 確認項目

| ID | 確認内容 | 主な検証先 |
| --- | --- | --- |
| R355-001 | 初回は開始日、履歴がある場合は解決日＋N日。開始日基準の既存方式は不変 | CompletionRecurrenceTest |
| R355-002 | 遅れて完了しても元の実行日は維持し、解決日を次回の起点にする | RoomTodoRecurrenceSpecCoverageTest |
| R355-003 | 未完了の1回分だけを翌日以降表示し、長期未起動・再整合でも増殖しない | ドメイン・Room回帰 |
| R355-004 | スキップも起点を進め、完了と異なる履歴として保存する | Room回帰 |
| R355-005 | 当日の取り消しとそのUndoで次回が戻り、過去の確定履歴は変えない | Room回帰 |
| R355-006 | 一日の終了時刻をまたぐ操作は解決論理日を使用する | ドメイン・Room回帰 |
| R355-007 | N=1・999、0・1000・空入力の境界を検証する。入力は既存同様数字3桁まで | ドメイン・ViewModel |
| R355-008 | 編集で間隔・開始日等を変えた場合、履歴を消さず次回だけ再計算する | Room回帰・OWNER |
| R355-009 | 終了日は新しい回の生成を止めるが、既存の未完了分は解決まで残す | ドメイン・Room回帰 |
| R355-010 | 通知は既知の次回1件だけを対象とし、未完了を理由に毎日再通知しない | ドメイン通知試験・OWNER |
| R355-011 | 一覧とウィジェットが同じ予定を表示し、起点が通常取得期間より前でも一致する | Room・ウィジェット回帰 |
| R355-012 | 二重操作・古い通知／ウィジェットの操作で重複や別サイクルの誤完了が起きない | Room回帰・OWNER |
| R355-013 | アーカイブ中は非表示、復元で1回分だけ再開し、休止期間を補完しない | Room回帰 |
| R355-014 | 形式7往復と形式1～6読取を確認し、偽装旧形式・繰り越しfalse・不正間隔は復元前に拒否する | BackupSpecCoverageTest・BackupArchiveReaderTest |
| R355-015 | 編集画面で方式選択・間隔入力・ON固定と理由・次回1件のプレビューを確認し、詳細画面でも識別できる | UI回帰・OWNER |

## 2. 動作確認手順

1. TODO編集で「前回完了日からN日後」を選び、N=3で保存する。繰り越しがON固定で、次回1件だけが表示されることを確認する。
2. 完了またはスキップ後、編集プレビューの次回が操作した論理日から3日後になることを確認する。カレンダー履歴から当日の取り消しを行い、元の未完了へ戻ることも確認する。
3. ウィジェットからも完了・スキップを行い、一覧・次回予定が一致することを確認する。
4. 編集で間隔を変更し、終了日・アーカイブ復元・形式7バックアップ／復元を確認する。

端末時刻の変更、実ユーザーデータの削除や公開版のアンインストールを必須手順にしない。遅延完了・長期未起動等の時間経過は可変Clockの自動試験で検証する。

## 3. 結果の扱い

実装時のローカル自動試験結果と、OWNERの表示・実操作の確認結果を分けて記録する。未実施の確認を既存424件の合格で補完しない。

### 2026-11-13 ローカル確認

- 対象: `feature/repeat-after-completion-355`の未コミット変更。公開版のversionCodeは更新していない。
- 単体試験: `testDebugUnitTest` 252件合格、失敗・スキップ0。今回追加はドメイン10件とViewModel1件。
- Android試験: 読み取り専用・非表示で起動した`MATA_Store_Phone_API34`（Android 14 / API 34）の`emulator-5580`で93件合格、失敗・スキップ0。今回追加は13件。
- `lintDebug`、`assembleDebug`、Android試験APKのビルドに成功。Lintのエラー0。警告37件・Hint 3件があるため「警告なし」とは扱わない。
- 法務ページ、ストア原稿、試験担当割当、試験結果、既存自動化証跡、アーキテクチャ、外部データ契約の7検証が成功。`git diff --check`と変更Markdown 19文書の相対リンク確認も成功。
- Roomスキーマ・依存関係・Manifest・公開済みversionCode 11のAABは変更していない。過去の試験結果424件も変更していない。

Android試験の内訳:

| 試験クラス | 合格数 |
| --- | --- |
| RoomTodoRecurrenceSpecCoverageTest | 21 |
| BackupArchiveReaderTest | 8 |
| BackupSpecCoverageTest | 16 |
| TodoEditorScreenSpecCoverageTest | 15 |
| CalendarHistoryScreenSpecCoverageTest | 17 |
| NotificationSchedulerTestSpecCoverageTest | 16 |

実行したGradleタスクは`testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest`。上記6クラスを選択し、依存関係のstrict検証を有効化、アップロード署名情報は検証コマンドのプロパティで無効化して実行した。

ローカル成果物とレポート:

- `app/build/outputs/apk/debug/app-debug.apk`（MATA Dev / `com.mochisofts.mata.debug`）
- APK SHA-256: `A893D2C3716BDDB7D9EAADB4907A62397646E0F600F5E5331028BFB993FCFF12`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

形式7バックアップは今回の実装で復元し、形式6までの旧公開版へ読み込ませない。

### 2026-11-16 OWNER確認

- OWNERより「全て動作確認出来ました」との報告を受領。第2節の表示・実操作の確認はすべて問題なし。
- 対象は前回案内したDebug実装。確認端末・Androidバージョンは今回の報告では未申告のため、過去の確認環境を流用しない。
- OWNER確認を受け、コミット・PR作成とGitHub CI確認へ進む。マージ結果はPRに記録し、修正版のGoogle Play配布は別作業とする。
