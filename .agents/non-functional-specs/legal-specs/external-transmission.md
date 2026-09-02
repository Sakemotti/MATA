# 外部送信に関する公表仕様

- 文書状態: 方針確定・公開前原稿・専門家確認推奨
- 最終更新日: 2026-09-02
- 公開URL: `https://mochisofts.com/mata/external-transmission`
- 公開用HTML: `legal-site/mata/external-transmission/index.html`

## 1. 方針

電気通信事業法上の外部送信規律の適用有無にかかわらず、利用者がMATAと法的文書サイトからの外部送信を確認できるよう公表する。

公表では、送信され得る情報、送信先を取り扱う者、MATAでの利用目的、送信先での利用目的および利用者が選択できる停止方法を、専門用語だけに依存せず日本語で示す。アプリの設定画面からプライバシーポリシーを経由して本ページへ容易に到達できるようにする。

## 2. アプリからの外部送信

| サービス・情報を取り扱う者 | 送信され得る情報 | MATAでの目的 | 送信先での主な目的 | 選択・停止方法 |
| --- | --- | --- | --- | --- |
| Google Mobile Ads（AdMob）/ Google | IPアドレス、おおよその位置、広告ID・アプリセットID等の識別子、アプリ・広告操作、診断、端末・アプリ情報 | バナー広告の配信、表示、測定、不正防止 | 広告配信、パーソナライズ、測定、分析、不正防止、サービス改善 | 対象地域ではUMPのプライバシー設定と端末の広告設定を利用できる。 |
| User Messaging Platform（UMP）/ Google | IPアドレス、端末・アプリ情報、地域判定、同意・プライバシー選択状態、通信情報 | 広告に関する同意・選択肢の取得と管理 | 同意状態の管理、法令・ポリシー対応 | 対象地域ではアプリのプライバシー設定から選択肢を再表示できる。 |
| Holidays JP / holidays-jpプロジェクト | 要求先URL、IPアドレス、固定User-Agent「MATA」、アクセス日時、HTTPキャッシュ情報 | 日本の祝日データ取得と繰り返し計算 | 祝日データ配信、運用、セキュリティ | アプリは定期的に祝日情報を更新する。端末のネットワークを無効にすると送信されないが、祝日情報が更新されない場合がある。 |
| Google Play / Android vitalsを提供するGoogle | クラッシュ、応答なし、端末・OS・アプリバージョン、性能・安定性情報 | 配布、互換性、品質、障害の確認 | Google PlayとAndroidの運用、品質・安全性の改善 | Google Playおよび端末の設定に従う。MATA独自の利用分析・クラッシュ自動送信SDKは使用しない。 |

MATAは、TODOのタイトル、説明、カテゴリ名、履歴または手動バックアップを上記サービスの要求へ含めない。

## 3. Webサイト閲覧時の外部送信

| サービス・情報を取り扱う者 | 送信され得る情報 | 目的 | 停止方法 |
| --- | --- | --- | --- |
| GitHub Pages / GitHub, Inc. | IPアドレス、User-Agent、アクセス日時、要求URLその他Web閲覧に通常必要な通信情報 | 法的文書の配信、セキュリティ、サービス運用 | 法的文書サイトを閲覧しない場合は送信されない。 |

初期公開時点で、Webサイト独自のアクセス解析、広告、問い合わせフォームおよび独自Cookieを設置しない。

## 4. 変更管理と確認

- SDK、通信先、送信情報または利用目的を変更する前に本仕様、公開ページ、プライバシーポリシー、Data safetyおよびUMPを更新する。
- Releaseビルドの通信検査で、掲載先以外の通信先とユーザー入力の送信がないことを確認する。
- 送信先事業者の正式名称と利用目的を公開候補ごとに確認する。
- 外部送信規律の適用範囲と公表方法を本番公開前に専門家へ確認する。

## 5. 公式資料

- [外部送信規律FAQ（総務省）](https://www.soumu.go.jp/main_sosiki/joho_tsusin/d_syohi/gaibusoushin_kiritsu_00002.html)
- [Google プライバシーポリシー](https://policies.google.com/privacy?hl=ja)
- [AdMob Android data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [Holidays JP](https://holidays-jp.github.io/)
- [GitHub Privacy Statement](https://docs.github.com/ja/site-policy/privacy-policies/github-general-privacy-statement)
