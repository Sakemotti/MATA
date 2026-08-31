# リリース記録仕様

- 文書状態: 確定
- 最終更新日: 2026-08-31
- 親仕様: [リリース・配布運用仕様](README.md)
- 関連文書: [リリースチェックリスト](release-checklist.md)、[Release事前検査仕様](release-preflight.md)

## 1. 目的

各Release候補について、ソース、署名済み成果物、自動検査、実機・Google Play試験、承認、公開トラックおよび障害を一つの機械可読な記録へ関連付ける。記録は公開可否の判断根拠と、公開後の調査、停止、Hotfixおよび監査に使用する。

## 2. 生成と保存

- 手動の`Release candidate` workflowは、証跡artifactのアップロード後に`release-record-draft.json`を自動生成する。
- 候補記録はReleaseメタデータと`evidence-manifest.json`からアプリ識別子、バージョン、commit、ビルド日時、Upload Key証明書SHA-256および証跡マニフェストSHA-256を取得する。
- GitHub Actionsからrepository、workflow、run ID、run attempt、証跡artifact ID、認証付きURLおよびartifact SHA-256を取得する。
- 候補記録は証跡artifactとは別のActions artifactへ保存し、両artifactのIDとSHA-256を実行サマリーへ記録する。
- Internal testingへ進める候補だけを`release-records/v<versionName>-<versionCode>.json`へコピーし、段階ごとの結果を追記する。
- Production公開後の確定記録はレビューを経てGit管理する。公開済み記録の過去値を消さず、訂正理由を`notes`へ追記する。
- Actions artifactの期限前に、確定記録と記録が指す公開成果物を別の安全な保管先へ移す。

## 3. 記録項目

| 区分 | 必須項目 |
| --- | --- |
| アプリ | Application ID、versionName、versionCode、Git commit、公開後のsource tag |
| ビルド | UTCビルド日時、workflow run、証跡artifact ID・URL・SHA-256、証跡マニフェストSHA-256、Upload Key証明書SHA-256 |
| 検査 | 自動Releaseゲート、ダウンロード証跡、P0/P1、Pre-launch report、Internal testing、Closed testingの状態・確認日時・担当 |
| 法的公開 | HTTPS公開とリンク検査の状態・確認日時・担当 |
| Google Play | 現在のtrack、状態、Console上のrelease ID、提出・公開日時、rollout率、track変更履歴 |
| 承認 | 技術承認、公開承認の状態・日時・担当 |
| 障害 | 管理ID、S0～S3、状態、機密情報を含まない要約 |
| 備考 | 判断理由、例外、訂正理由など機密情報を含まない文章 |

状態確認の担当者はGitHubユーザー名または事前に定義した役割名を記録する。氏名、メールアドレス、テスター一覧、TODO、バックアップ、購入トークン、注文ID、鍵、パスワードその他の秘密情報を記録しない。

## 4. 段階と機械検査

`release-record.mjs verify`は次の段階を検査する。

| 段階 | `recordState` | 必須条件 |
| --- | --- | --- |
| `candidate` | `candidate` | 自動Releaseゲート合格、公開前、track履歴なし |
| `internal` | `internal_verified` | 証跡、P0/P1、Pre-launch report、Internal testing、法的公開、技術承認が合格し、Internal track完了履歴がある |
| `closed` | `closed_verified` | Internalの条件に加えClosed testingが合格し、Closed track完了履歴がある |
| `production` | `published` | 全検査と両承認が合格し、source tag、Production release ID、公開日時、100% rolloutおよびProduction完了履歴がある |

Production段階では未解決または緩和中の障害を許可しない。残存リスクを受容して公開する場合は障害を`accepted`とし、公開承認の判断理由を記録する。

## 5. 更新手順

1. Actionsから候補記録artifactをダウンロードする。
2. 証跡ZIPと展開後ファイルを[Release事前検査仕様](release-preflight.md)に従って検査する。
3. 候補記録の`downloadedEvidence`へ`passed`、確認日時、担当を記録する。
4. 各試験・法的公開・承認・Google Play操作の直後に対応する状態とtrack eventを追記する。
5. 段階を進める前に該当する`--stage`で機械検査する。
6. Production公開後にsource commitへ`v<versionName>`タグを付与し、確定記録を`production`として検査する。
7. 確定記録をレビューし、`release-records`へコミットする。

実行例は[リリース記録の運用手順](../../../release-records/README.md)に従う。
