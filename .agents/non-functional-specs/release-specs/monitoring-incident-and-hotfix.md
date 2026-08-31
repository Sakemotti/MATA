# 公開監視・障害対応・Hotfix仕様

- 文書状態: 確定
- 最終更新日: 2026-08-31
- 親仕様: [リリース・配布運用仕様](README.md)
- 関連文書: [リリース記録仕様](release-records.md)、[クラッシュ・品質監視仕様](../observability-specs/crash-and-quality-monitoring.md)、[セキュリティ・プライバシーの検証・公開判定仕様](../security-privacy-specs/verification-and-release.md)

## 1. 目的

Google Playでのテスト・本番公開後に品質またはセキュリティ上の異常を検知した場合に、利用者への追加配布を抑え、証拠を保全し、前方修正した新しいversionCodeを安全に公開する。Play Console操作を自動化せず、操作前後の状態と判断をリリース記録へ残す。

## 2. 監視チェックポイント

リリース記録の`monitoring`へ、次のチェックポイントごとに観測日時、担当、各信号の状態、判断および機密情報を含まない要約を追記する。

| チェックポイント | 実施時期 |
| --- | --- |
| `internal` | Internal testing完了時 |
| `closed` | Closed testing完了時 |
| `production_initial` | 初回本番公開直後または更新のProduction開始直後 |
| `rollout_10` / `rollout_25` / `rollout_50` | 更新版の各段階を24時間以上監視した後 |
| `production_100` | 100%配布後24時間の重点監視を完了した時 |
| `stable_weekly` | 安定後の週次確認 |

観測信号はAndroid vitals、レビュー、Billing、広告、ポリシー状態とし、`healthy`、`degraded`、`threshold_exceeded`または`unavailable`で記録する。取得不能を正常扱いせず`unavailable`とする。判断は`continue`、`hold`または`halt`とする。

`production`段階の確定には、`production_100`で全信号が`healthy`かつ判断が`continue`であることを要求する。数値指標を記録する場合も、個人や端末を識別できる明細を保存しない。

## 3. 重大度と配布判断

| 重大度 | 例 | 配布判断 |
| --- | --- | --- |
| S0 | データ漏えい、Upload Key・資格情報侵害、広範なデータ消失 | 直ちに`halt`。段階公開停止、必要なら新規公開停止、鍵失効、法的通知確認 |
| S1 | 起動不能、DB移行失敗、購入権利誤判定、繰り返すANR、データ消失疑い | 直ちに`halt`し、当日中に切り分け。Hotfixを最優先 |
| S2 | 一部機能クラッシュ、通知誤発火、主要操作不能 | 次段階への拡大を`hold`。影響が拡大または回避不能ならS1へ引き上げ |
| S3 | 軽微な表示・性能問題 | 原則として配布継続。Issue化し通常の修正版へ含める |

Core vitalの不良動作しきい値超過、データ移行失敗、購入権利誤判定またはS0/S1を検知した場合、再現率が未確定でも配布停止を優先する。停止判断に事前承認を要求しない。停止後の再開または修正版公開には技術承認を必要とする。

## 4. 停止時の記録

1. 対象リリース記録へ監視判断`halt`を追記する。
2. `incidents`へ一意な管理ID、S0/S1、`open`、機密情報を含まない要約を追加する。
3. Play Consoleで対象trackのrelease ID、現在割合、最終更新時刻を確認する。
4. 段階公開の場合は`Halt rollout`を実行し、同じrelease IDの`halted` track eventを追記する。
5. 初回公開または100%公開済みで新規利用者への提供も止める必要がある場合は、影響と代替手段を確認したうえでアプリを非公開にする。
6. `recordState`を`halted`、`publication.status`を`halted`へ更新し、`release-record.mjs verify --stage halted`を実行する。

段階公開を停止しても、すでに対象となった利用者の端末は旧版へ戻らない。アプリを非公開にしても既存利用者は利用と更新を継続できるため、データ破損等の進行を止める手段とはみなさない。

## 5. 初動と証拠保全

- 影響versionCode、発生開始、対象OS・端末群、機能、再現条件および回避策を特定する。
- Android vitals、Pre-launch report、Play Consoleのrelease・rollout履歴、対象commit、AAB、mapping、SBOM、リリース記録を保全する。
- TODO、カテゴリ、バックアップ、購入トークン、注文ID、広告IDまたはテスター個人情報の提出を求めない。
- 診断範囲を障害分類、件数、OS、端末モデル等の集計情報へ限定し、場当たり的な収集SDKを追加しない。
- 原因調査中に履歴を書き換えたり、公開済みGitタグを移動・再利用したりしない。
- S0ではGoogle Play、SDK提供者、利用者、監督機関等への通知要否と期限を専門家へ確認する。

## 6. 鍵・資格情報の侵害

- GitHub EnvironmentまたはRepository Secretへのアクセスを直ちに制限し、侵害した値を無効化する。
- Upload Keyの紛失・侵害では新しいUpload Keyを作成し、Play App SigningのUpload Key resetをアカウント所有者から申請する。古い鍵を再利用しない。
- Upload KeyとGoogle Playが保持するApp signing keyを区別し、App signing keyの問題をUpload Keyの差し替えだけで解決したと判断しない。
- 新しい証明書SHA-256を安全な別経路で確認し、GitHub Secret、Variableおよびリリース基準値を更新する。
- GitHub、Google、AdMob等のアクセストークン侵害では、該当サービス側で失効・再発行し、ログやIssueへ値を貼り付けない。

## 7. 再開とHotfix

停止したAAB自体に問題がないと確認できた場合だけ、同じreleaseの段階公開を再開できる。バイナリ、設定またはデータ処理に問題がある場合は再開せず、次の手順で前方修正する。

1. 配布済みsource tagまたはcommitから`hotfix/<incident-id>`を作成する。
2. 影響を止める最小修正を行い、必要な仕様・試験・法的文書・申告も同時更新する。
3. versionCodeを必ず増加させ、利用者影響が変わる場合はversionNameのpatchを増加させる。
4. DBをダウングレードせず、問題版が作成したデータを安全に読む前方マイグレーションと回帰試験を追加する。
5. 通常のRelease candidate workflow、証跡検査、リリース記録、最低限のInternal testingおよびPre-launch reportを省略しない。
6. Hotfixのリリース記録へ元のincident IDを記載し、修正commit、回避策、検証結果を関連付ける。
7. 修正版も更新時の段階公開を使用し、同じ監視チェックポイントで拡大する。

Google Play上の既存releaseを修復または同じversionCodeで置換できる前提を置かない。問題版を受け取った利用者には、より大きいversionCodeの修正版を配布する。

## 8. 終了条件

- 原因、影響範囲、封じ込め、修正、検証および再発防止が記録されている。
- S0/S1の障害状態が`resolved`または明示的に承認された`accepted`である。
- 修正版のInternal、Closed、Productionおよび100%後監視が合格している。
- 必要な利用者通知、法的文書、Data safety、SDK申告およびストア説明が更新されている。
- 元の停止記録とHotfix記録の相互参照がある。

## 9. 公式資料

- [Release app updates with staged rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)
- [Update or unpublish your app](https://support.google.com/googleplay/android-developer/answer/9859350)
- [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
