# MATA リリース記録

このディレクトリには、Google Playへ進めたRelease候補の確定記録を保存します。候補ごとに`v<versionName>-<versionCode>.json`を1ファイル作成します。

## 候補記録の取得

`Release candidate` workflowが成功すると、次の2つのartifactが生成されます。

- `mata-release-candidate-<commit>`: 署名済みAABと全証跡
- `mata-release-record-<commit>`: `release-record-draft.json`

実行サマリーで両artifactのIDとSHA-256を確認します。候補記録をこのディレクトリへコピーする場合は、例えば初回版を`v1.0.0-1.json`とします。

## 段階別の検査

候補生成直後:

    node tools/release/release-record.mjs verify --record release-records/v1.0.0-1.json --stage candidate

Internal testing完了後:

    node tools/release/release-record.mjs verify --record release-records/v1.0.0-1.json --stage internal

Closed testing完了後:

    node tools/release/release-record.mjs verify --record release-records/v1.0.0-1.json --stage closed

Production公開完了後:

    node tools/release/release-record.mjs verify --record release-records/v1.0.0-1.json --stage production

JSONの状態、日時、担当、Google Play release ID、track eventは実際の操作直後に更新します。状態を推測して事前入力しません。

## 禁止事項

- Upload Key、keystore、パスワード、アクセストークンを保存しない。
- 購入トークン、注文ID、TODO、バックアップファイルを保存しない。
- テスターの氏名、メールアドレス、Googleアカウント一覧を保存しない。
- 公開済み記録の過去のtrack eventや障害を削除しない。

項目定義と段階条件は[リリース記録仕様](../.agents/non-functional-specs/release-specs/release-records.md)を正とします。
