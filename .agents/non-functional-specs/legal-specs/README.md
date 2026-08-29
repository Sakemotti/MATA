# 法的文書仕様

- 文書状態: 方針確定・公開前原稿
- 最終更新日: 2026-08-29
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[セキュリティ・プライバシー仕様](../security-privacy-specs/README.md)、[収益化仕様](../../functional-specs/monetization-specs/README.md)、[リリース・配布運用仕様](../release-specs/README.md)

## 1. 目的

MATAの一般公開に必要な法的文書の公開前原稿、公開URLおよび更新手順を一元管理する。本文は2026年8月27日時点の仕様と公式資料を前提とした原稿であり、法的助言そのものではない。特に個人事業者として公開する氏名、住所、電話番号および特定商取引法上の表示方法は、公開前に専門家へ確認する。

## 2. 文書構成と公開先

| 文書 | 原稿 | 公開URL |
| --- | --- | --- |
| プライバシーポリシー | [privacy-policy.md](privacy-policy.md) | `https://mochisofts.com/mata/privacy` |
| 利用規約 | [terms-of-use.md](terms-of-use.md) | `https://mochisofts.com/mata/terms` |
| 特定商取引法に基づく表記 | [commercial-transactions.md](commercial-transactions.md) | `https://mochisofts.com/mata/commercial-transactions` |
| 外部送信に関する公表 | [external-transmission.md](external-transmission.md) | `https://mochisofts.com/mata/external-transmission` |

このリポジトリのMarkdownを正本原稿とし、公開時は内容を省略せず静的HTMLへ変換する。アプリに直接表示するのは既存仕様どおりプライバシーポリシーと利用規約へのリンクとし、他の2文書はWebサイトと各文書間のリンクから到達可能にする。

公開サイトのHTML/CSS雛形、ローカル検証手順および誤公開防止の公開用検証は[法的文書サイト](../../../legal-site/README.md)で管理する。雛形には未確定値と検索除外が残るため、同READMEの公開前作業を完了するまで本番へ配置しない。

## 3. 運営者と連絡先

| 項目 | 確定値 |
| --- | --- |
| アプリ提供者 | Mochisofts（個人運営） |
| 問い合わせ先 | `com.mochisofts@gmail.com` |
| 対応言語 | 日本語 |
| 対象地域 | 日本 |

戸籍上の氏名、住所および電話番号は本リポジトリへ登録せず、公開前原稿ではプレースホルダーとする。公開方法を法的に確認した後、公開サイト側で確定値または適法な請求時開示の案内へ置き換える。

## 4. 公開方式

- `mochisofts.com`をカスタムドメインとするGitHub Pagesの静的公開を採用する。
- アプリのソースを管理するPrivateリポジトリをそのまま公開元にせず、無料公開用のPublicリポジトリ`Sakemotti/mochisofts.github.io`を別途作成する。リポジトリ作成、DNS設定および初回公開は別作業とする。
- 公開サイト用リポジトリは`main`ブランチのルートから公開し、MATAリポジトリから長期PATを使って自動pushしない。
- 公開前に`legal-site/package-release.ps1`を実行し、公開用検証を通過したHTML、CSS、`CNAME`、`app-ads.txt`等の許可対象だけを空の出力先へ生成する。原稿Markdown、テンプレート、READMEおよび検証スクリプトを公開リポジトリへ含めない。
- 認証、地域制限、Cookie同意を経ないと本文を読めない構成、PDFだけの公開、JavaScriptを必須とする本文表示を使用しない。
- HTTPSを強制し、モバイルブラウザーから全文を閲覧可能にする。
- Webサイト独自のアクセス解析、広告および問い合わせフォームは初期公開では設けない。
- ルートには各文書への導線を置き、`https://mochisofts.com/app-ads.txt`も同じサイトで公開する。

## 5. 変更管理

1. 権限、外部SDK、通信先、収集・共有データ、課金、対象年齢または問い合わせ先を変更する前に4文書への影響を確認する。
2. 原稿、公開HTML、Google Play Data safety、ストア掲載およびUMPメッセージを同じ実装へ一致させる。
3. 実質的な変更では改定日と変更内容を更新し、必要に応じてアプリ内またはリリースノートで通知する。
4. 旧版を追跡できるよう、公開内容をGitで版管理する。

## 6. 公開ブロッカー

- [ ] 特定商取引法に基づく表記の氏名、住所、電話番号および表示方法を専門家へ確認した。
- [ ] すべての`[公開前に確定]`、`[初回公開日]`等のプレースホルダーを解消した。
- [ ] 利用規約と特定商取引法に基づく表記を専門家が確認した。
- [ ] GitHub Pages、カスタムドメイン、DNSおよびHTTPSを設定した。
- [ ] 4文書と`app-ads.txt`を認証不要で閲覧でき、各URLが最終本文を返すことを確認した。
- [ ] ReleaseビルドのSDK・通信検査結果と文書、Data safetyおよびUMPの内容が一致することを確認した。

## 7. 主な公式資料

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [AdMob Android data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [通信販売広告について（消費者庁）](https://www.no-trouble.caa.go.jp/what/mailorder/advertising.html)
- [外部送信規律FAQ（総務省）](https://www.soumu.go.jp/main_sosiki/joho_tsusin/d_syohi/gaibusoushin_kiritsu_00002.html)
- [GitHub Pages](https://docs.github.com/pages/getting-started-with-github-pages)
