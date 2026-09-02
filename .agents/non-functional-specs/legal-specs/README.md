# 法的文書仕様

- 文書状態: 公開運用開始・専門家確認前
- 最終更新日: 2026-09-02
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[セキュリティ・プライバシー仕様](../security-privacy-specs/README.md)、[収益化仕様](../../functional-specs/monetization-specs/README.md)、[リリース・配布運用仕様](../release-specs/README.md)

## 1. 目的

MATAの一般公開に必要な法的文書、公開URLおよび更新手順を一元管理する。公開用HTMLは`legal-site`配下を正本とし、本仕様は掲載要件と公開ブロッカーを定義する。

本仕様と公開前原稿は法的助言そのものではない。特に個人事業者としての特定商取引法上の表示方法と外部送信規律の適用範囲は、本番公開前に専門家へ確認する。

## 2. 文書構成と公開先

| 文書 | 公開用HTML | 公開URL |
| --- | --- | --- |
| プライバシーポリシー | `legal-site/mata/privacy/index.html` | `https://mochisofts.com/mata/privacy` |
| 利用規約 | `legal-site/mata/terms/index.html` | `https://mochisofts.com/mata/terms` |
| 特定商取引法に基づく表記 | `legal-site/mata/commercial-transactions/index.html` | `https://mochisofts.com/mata/commercial-transactions` |
| 外部送信に関する公表 | `legal-site/mata/external-transmission/index.html` | `https://mochisofts.com/mata/external-transmission` |

特定商取引法に基づく表記の掲載要件は[commercial-transactions.md](commercial-transactions.md)、外部送信の掲載要件は[external-transmission.md](external-transmission.md)に定める。プライバシーのデータ要件は[データ・プライバシー仕様](../security-privacy-specs/data-and-privacy.md)、購入と払い戻しは[課金・権利状態仕様](../../functional-specs/monetization-specs/billing-and-entitlement.md)にも従う。

本リポジトリから公開用GitHub Pagesリポジトリへ反映する手順は[法的サイト公開・同期運用仕様](publishing-workflow.md)に定める。

## 3. 運営者と連絡先

| 項目 | 確定値 |
| --- | --- |
| アプリ提供者 | Mochisofts（個人運営） |
| 問い合わせ先 | `com.mochisofts@gmail.com` |
| 対応言語 | 日本語 |
| 対象地域 | 日本 |

戸籍上の氏名、住所および電話番号は本リポジトリへ登録しない。特定商取引法に基づく表記では、消費者庁の現行案内に基づく請求時開示を採用し、購入申込みの意思決定前に十分な時間をもって遅滞なく電子メールで提供できる運用を必須とする。

Google Playを決済手段として使用することだけを理由に、販売主体としての表示や問い合わせ対応が不要とは扱わない。払い戻しはGoogle Playの仕組みを利用できるが、適用法令上の利用者の権利を一律に制限しない。

## 4. 公開方式

- `mochisofts.com`をカスタムドメインとするGitHub Pagesの静的公開を採用する。
- GitHub Pages用の公開リポジトリ、カスタムドメイン、DNSおよびHTTPSは設定済みとする。
- 本リポジトリの`legal-site`を正本、公開リポジトリを配信用コピーとし、正本の`main`へマージした後にユーザーがコピーする。
- 認証、地域制限、Cookie同意を経ないと本文を読めない構成、PDFだけの公開、JavaScriptを必須とする本文表示を使用しない。
- HTTPSを強制し、スマートフォンから4文書の全文を閲覧可能にする。
- Webサイト独自のアクセス解析、広告、問い合わせフォームおよび独自Cookieは初期公開では設けない。
- ルートページと各法的文書から4文書へ到達できるようにする。
- `app-ads.txt`はAdMobの正式なパブリッシャーID取得後に同じサイトで公開する。

## 5. 変更管理

1. 権限、外部SDK、通信先、収集・共有データ、課金、対象年齢または問い合わせ先を変更する前に4文書への影響を確認する。
2. 仕様、公開HTML、Google Play Data safety、ストア掲載、UMPメッセージおよびアプリ実装を同時に一致させる。
3. 実質的な変更では改定日と変更内容を更新し、必要に応じてアプリ内またはリリースノートで通知する。
4. 公開内容をGitで版管理し、公開日と対応するリリースを追跡可能にする。
5. 正本側commitと公開側commitを対応付け、公開リポジトリだけに存在する本文変更を作らない。

## 6. 公開ブロッカー

- [ ] 特定商取引法に基づく表記の請求時開示方法を専門家へ確認した。
- [ ] 氏名、住所、電話番号の開示請求へ購入前に遅滞なく対応できる運用を準備した。
- [ ] 利用規約、特定商取引法に基づく表記および外部送信に関する公表を専門家が確認した。
- [ ] 制定日、最終改定日、提供者、連絡先、商品および価格方針が実際の公開内容と一致する。
- [ ] GitHub Pages、カスタムドメイン、DNSおよびHTTPSを設定した。
- [ ] 4文書と`app-ads.txt`を認証不要で閲覧でき、各URLが最終本文を返すことを確認した。
- [ ] ReleaseビルドのSDK・通信検査結果と4文書、Data safetyおよびUMPの内容が一致することを確認した。

## 7. 主な公式資料

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [AdMob Android data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [通信販売広告Q&A（消費者庁）](https://www.no-trouble.caa.go.jp/qa/advertising.html)
- [インターネット通信販売のルール（消費者庁）](https://www.no-trouble.caa.go.jp/what/mailorder/rule.html)
- [外部送信規律FAQ（総務省）](https://www.soumu.go.jp/main_sosiki/joho_tsusin/d_syohi/gaibusoushin_kiritsu_00002.html)
- [GitHub Pages](https://docs.github.com/pages/getting-started-with-github-pages)
