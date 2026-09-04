# リリース別試験結果の管理

- 現在の結果台帳: [MATA 1.0.0 (1)](initial-release-results.tsv)
- 実施記録: [初回Closed testing実施台帳](closed-testing-log.md)
- 検証ツール: [`tools/test-specs/verify-results.mjs`](../../tools/test-specs/verify-results.mjs)

## 1. 目的

総合動作確認項目書の403件を仕様書原本と分離し、リリース候補ごとに結果を保存する。TSVは表計算ソフトでも編集でき、Git上では項目ごとの差分を確認できる。

## 2. 列

| 列 | 内容 |
| --- | --- |
| `id` | 仕様書の固定試験ID。編集しない |
| `priority` | `P0`、`P1`、`P2`。仕様書と自動照合する |
| `specFile` | 条件・操作・期待結果を記載した仕様ファイル |
| `result` | `未実施`、`合格`、`不合格`、`保留`、`対象外` |
| `executedAt` | `YYYY-MM-DD`またはISO 8601日時 |
| `testerId` | `T01`等の匿名ID。自動検査は`AUTO` |
| `versionCode` | 実際に確認した正の整数 |
| `environment` | 端末、Android、CI等の必要な条件 |
| `evidence` | 実施ID、CI URL、画像、ログ、Issue、対象外理由等 |

`未実施`の行では実行情報5列を空欄にする。それ以外では全列を記入する。タブと改行をセル内へ入力しない。

## 3. 更新手順

1. 対応する`specFile`で条件・操作と期待結果を確認する。
2. [Closed testing実施台帳](closed-testing-log.md)へ実施IDと詳細を記録する。
3. TSVの同じ試験IDへ結果、日時、テスターID、versionCode、環境、証跡を記録する。
4. 不合格は1項目ずつIssueへ関連付ける。修正後の再試験に合格した場合は、証跡へ初回不合格と再試験の両方を残す。
5. 次のコマンドを実行する。

```powershell
node tools/test-specs/verify-results.mjs
```

検証では、仕様書とのID集合、優先度、参照ファイル、重複、許可された結果値、必須実行情報および件数を確認し、優先度別集計を出力する。

## 4. 将来のリリース

新しいリリース候補では、既存結果を無条件にコピーせず、次のコマンドで空の台帳を作成する。

```powershell
node tools/test-specs/verify-results.mjs --initialize .agents/test-specs/release-results-VERSION.tsv
```

自動検査等を再利用できる場合も、新しい対象commitとversionCodeで再実行した証跡を記録する。仕様書へ試験IDを追加・変更した場合、古い台帳の検証は意図的に失敗するため、対象リリースの仕様スナップショットと結果台帳を同時に更新する。

## 5. 公開判定

- P0とP1はすべて`合格`を必須とする。
- P2の`不合格`、`保留`、`対象外`には影響と判断理由を記録する。
- `対象外`は環境と理由を証跡へ明記し、単なる未実施の代用にしない。
- 自動テストと試験IDが1対1で対応しない場合は、推測で合格へ変更しない。
- 氏名、Googleアカウント、メールアドレス、実際のTODO内容等の個人情報をTSVへ記録しない。
