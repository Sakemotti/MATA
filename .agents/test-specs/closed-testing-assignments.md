# 初回リリース試験の実施区分

- 対象: MATA `1.0.0 (4)`
- 対象ソースcommit: `fe71e1579969d647e95170e47f2d036b19e5b657`
- 項目別区分: [initial-release-assignments.tsv](initial-release-assignments.tsv)
- 項目別結果: [initial-release-results.tsv](initial-release-results.tsv)
- 実施台帳: [初回Closed testing実施台帳](closed-testing-log.md)
- 追加テストの運用方針更新日: 2026-10-11
- 運営用フォロー計画: [追加Closed testingの利用・フォロー計画（14日間）](closed-testing-14-day-plan.md)

## 1. 方針

Closed testing参加者には総合動作確認項目の試験IDや正式確認の担当を割り当てない。2026-10-11のUSER方針に合わせ、最低限毎日アプリで何かしらのTODO管理を行い、実用した中での感想をGoogle Play非公開フィードバックへ送ることと、更新時の再確認を依頼する。日別操作・全機能の網羅・毎日の報告は指定しない。専門的な条件確認や全項目の合否判定はDEVとOWNERが行う。

総合動作確認項目424件は削除せず、公開判定に必要な開発者側の正式確認として管理する。Closed testingの自由操作結果だけを根拠に、条件を確認していない試験IDを合格にしてはならない。

| レーン | 担当 | 件数 | 対象 |
| --- | --- | ---: | --- |
| `CLOSED_TESTER` | 割り当てなし | 0 | Closed testing参加者は項目表を使わず自由操作する |
| `DEV_AUTO` | `DEV` | 335 | UNIT、Repository、DB、計算、通知スケジューラ、再現性のあるinstrumented UI等の自動検査 |
| `RELEASE_OWNER` | `OWNER` | 88 | 手動UI、E2E、実機環境、Console、法的文書および公開判定 |

各試験IDには正式確認の責任者を1件だけ設定する。外部テスターの自由操作で正確な条件と期待結果まで確認できた場合も、`OWNER`が報告内容を照合し、再現条件、版、端末および証跡を記録した後に項目別結果へ反映する。

`DEV_AUTO`の専用テスト335件は[自動試験証跡TSV](automated-test-evidence.tsv)でJUnitテストと1対1に対応する。既存テストが包括的に成功しただけで、未対応の試験IDを合格にしない。

## 2. Closed testing参加者への依頼

参加者へ[参加ガイド](closed-testing-tester-guide.md)の依頼文・参加手順と参加URLを共有する。[14日間フォロー計画](closed-testing-14-day-plan.md)は運営者側で使用し、テスターへの日別操作指示として配布しない。次は依頼しない。

- 個別の試験IDまたは担当範囲
- 固定の操作順序や正式試験の網羅チェックリスト
- 日別の機能指定、TODO件数や操作時間の指定、毎日の利用報告
- 端末設定の変更、境界時刻待ち、TalkBack等の専門的な確認
- バックアップファイル、端末全体のバグレポートまたは個人情報の提出

既存の匿名ID`T01`以降、新規募集の`R3-T01`以降は、参加日とフィードバックを同一人物へ対応付けるためだけに使用する。旧IDを別人に再利用しない。機能や試験項目の担当を意味しない。

## 3. 自由操作結果の扱い

- 「問題なし」という報告は、そのテスターの利用実績とフィードバックとして記録する。
- 操作内容や条件が不明な場合、特定の試験IDの合格証跡にはしない。
- 不具合・クラッシュ・データ欠損・誤通知・広告の重なり等は、試験IDとの対応が未確定でもIssue化できる。
- 複数テスターから同じ改善案が出た場合も、各報告を追跡できる形で記録する。
- Production access申請には、実際の利用期間、利用状況、フィードバックおよび対応結果だけを記載する。

## 4. 開発者側の実施手順

1. `OWNER`がPlay Consoleの参加状況を確認し、台帳へ匿名ID、参加日、端末・OSおよび最終利用確認日を記録する。
2. テスターから受けた自由記述を、台帳のフィードバック記録へ転記する。
3. 不具合報告は再現条件を確認し、必要に応じてIssueを作成する。
4. 総合動作確認項目の未実施P0/P1は、Closed testingとは別に`OWNER`が実機、エミュレータ、Pre-launch reportまたはConsoleで確認する。
5. 試験IDを合格にする場合は、結果TSVへ実施日、実施者、versionCode、環境および証跡を記録する。
6. 修正版を配布した場合は、影響範囲の自動試験と正式確認を再実施する。

## 5. 検証

```powershell
node tools/test-specs/verify-assignments.mjs
node tools/test-specs/verify-results.mjs
node tools/test-specs/verify-automated-evidence.mjs
```

割り当て検証は、424件のID漏れ・重複、仕様書との優先度・種別不一致、および正式確認が`DEV`か`OWNER`のどちらかへ割り当てられていることを確認する。Closed testing参加者への項目割り当てが残っている場合は失敗する。

## 6. 実施方法の変更

- `RELEASE_OWNER`の項目を自動化できた場合は、対応する専用テストと1対1証跡を追加し、種別へ`AUTO`を付けた後に`DEV_AUTO`へ変更する。
- `UI/INT/AUTO`は固定データとエミュレータで再現できるUI挙動に限定する。視覚品質、TalkBack、物理端末、外部アプリ・サービス連携は`RELEASE_OWNER`のままとする。
- エミュレータやPre-launch reportで代替する場合も、項目が要求する実機条件を満たすか確認する。
- 正式確認を外部テスターへ割り当てない。
- 区分変更後は割り当て検証を実行し、変更理由を記録する。
