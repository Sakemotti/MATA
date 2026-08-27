# MATA 外部送信に関する公表

- 文書状態: 公開前原稿
- 制定日: [初回公開日]
- 最終改定日: 2026-08-27
- 公開予定URL: `https://mochisofts.com/mata/external-transmission`

Mochisofts（個人運営）が提供するAndroidアプリ「MATA」および関連Webサイトでは、次の外部サービスへ利用者の端末から情報が送信される場合があります。本ページは、電気通信事業法上の適用有無にかかわらず、利用者が外部送信を確認できるよう公表するものです。

## 1. 本アプリからの外部送信

| サービス・送信先 | 送信され得る情報 | MATAでの利用目的 | 送信先での主な利用目的 | 利用者の選択・停止方法 |
| --- | --- | --- | --- | --- |
| Google Mobile Ads（AdMob）/ Google | IPアドレス、IPアドレス等から推定されるおおよその位置、広告ID、アプリセットID等の端末・アカウント識別子、アプリ操作、広告操作、診断情報、端末・アプリ情報 | バナー広告の配信、表示、測定、不正防止 | 広告配信、パーソナライズ、測定、分析、不正防止、サービス改善 | 対象地域ではUMPのプライバシー設定で選択できる。広告削除購入が有効な間は広告SDKを初期化しない。端末の広告設定も利用できる。 |
| User Messaging Platform（UMP）/ Google | IPアドレス、端末・アプリ情報、地域判定、同意またはプライバシー選択の状態、技術的な通信情報 | 広告に関する同意・選択肢の取得と管理 | 同意状態の管理、法令・ポリシー対応 | 対象地域ではアプリ内のプライバシー設定から選択肢を再表示できる。広告削除後も同意状態確認に必要な通信が発生する場合がある。 |
| Google Play Billing / Google | 商品ID、商品・価格の照会、購入状態、購入トークンその他Google Playが決済・権利確認に必要とする情報、端末・アプリ情報 | 広告削除商品の価格表示、購入、承認、復元、払い戻し・取消の反映 | 決済、購入管理、不正防止、法令・規約対応 | 購入操作を行わないことができる。価格照会と既存購入の復元にはGoogle Playとの通信が必要。 |
| Holidays JP | 要求先URL、IPアドレス、User-Agent、アクセス日時その他通常のHTTP通信情報 | 日本の祝日データ取得と「祝日を除外」する繰り返し計算 | 祝日データの配信、運用・セキュリティ | 祝日を除外する繰り返しを使用しない場合は、当該通信を必要とする機能の利用を避けられる。本アプリはTODO等を要求へ含めない。 |
| Google Play / Android vitals | クラッシュ、応答なし、端末・OS・アプリバージョン、性能・安定性に関する情報 | 配布、互換性・品質・障害の確認 | Google PlayとAndroidの運用、品質・安全性の改善 | Google Playおよび端末の設定に従う。本アプリは独自の利用分析・クラッシュ自動送信SDKを使用しない。 |

送信情報は、利用環境、Googleの設定、同意状態および各サービスの仕様により異なります。本アプリは、TODOのタイトル、説明、カテゴリ名、履歴または手動バックアップを上表のサービスへ送信しません。

## 2. Webサイト閲覧時の外部送信

| サービス・送信先 | 送信され得る情報 | 利用目的 | 停止方法 |
| --- | --- | --- | --- |
| GitHub Pages / GitHub | IPアドレス、User-Agent、アクセス日時、要求URLその他Web閲覧に通常必要な通信情報 | 法的文書の配信、セキュリティおよびサービス運用 | 法的文書をWebで閲覧しない場合は送信されない。アプリ利用に必要な文書は公開URLで確認できる。 |

提供者は、初期公開時点でWebサイトに独自のアクセス解析、広告、問い合わせフォームまたは独自Cookieを設置しません。

## 3. 各送信先の情報

- Google
  - [Google プライバシーポリシー](https://policies.google.com/privacy)
  - [Googleによる広告でのデータ利用](https://policies.google.com/technologies/ads)
  - [AdMob Android data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- Holidays JP
  - [Holidays JP](https://holidays-jp.github.io/)
- GitHub
  - [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)

## 4. 変更と問い合わせ

外部サービス、送信情報または利用目的を変更する場合は、実装前に本ページと[プライバシーポリシー](https://mochisofts.com/mata/privacy)を更新します。重要な変更はアプリ内表示またはリリースノート等でも知らせます。

本ページに関するお問い合わせは`com.mochisofts@gmail.com`へ連絡してください。
