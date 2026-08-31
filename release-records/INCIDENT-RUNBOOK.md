# 公開障害クイックRunbook

この文書は公開中の異常を検知した直後に使う短縮版です。詳細と判断基準は[公開監視・障害対応・Hotfix仕様](../.agents/non-functional-specs/release-specs/monitoring-incident-and-hotfix.md)を正とします。

## 最初に行うこと

1. 対象のversionCode、track、release ID、rollout率、検知日時を記録する。
2. S0～S3を暫定判定する。データ消失・履歴改変の疑いはS1以上とする。
3. S0/S1またはCore vitalしきい値超過なら、再現確認を待たず対象rolloutを停止する。
4. リリース記録へ`halt`の監視スナップショット、S0/S1 incident、`halted` track eventを追記する。
5. 次を実行し、記録不足がないことを確認する。

       node tools/release/release-record.mjs verify --record <record.json> --stage halted

6. AAB、mapping、SBOM、Actions証跡、Play Consoleのrollout履歴を保全する。

## やってはいけないこと

- 公開済みGitタグを移動・再利用しない。
- versionCodeを再利用しない。
- 問題版を端末上で旧版へダウングレードできると想定しない。
- TODO、バックアップ、購入トークン、注文ID、テスター情報を収集・記録しない。
- 原因未確認のまま同じreleaseを再開しない。
- Upload Key、パスワード、トークンをIssueやリリース記録へ貼らない。

## 次の判断

- 誤検知またはAAB外の一時的要因で、同じAABが安全と証明できた: 技術承認後に同じrolloutを再開する。
- バイナリ、設定、データ処理に問題がある: 新しいversionCodeのHotfixを作る。
- 新規利用者への提供も止める必要がある: 非公開化の影響を確認してPlay Consoleで実施する。既存利用者への停止・ダウングレードにはならない。
- Upload Key侵害: Secretアクセスを止め、新しいUpload Keyを作成し、アカウント所有者からresetを申請する。

## Hotfix最低条件

- 問題版のsource commit/tagを起点にする。
- 前方修正と必要なデータ互換性試験を追加する。
- Release candidate workflowと証跡検査を通す。
- Internal testingとPre-launch reportを省略しない。
- 元incident IDを新しいリリース記録へ残す。
