# リリース・配布運用仕様

- 文書状態: 確定
- 最終更新日: 2026-08-31
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[開発ガイドライン](../../development-guidelines.md)、[総合動作確認項目書](../../test-specs/README.md)、[収益化仕様](../../functional-specs/monetization-specs/README.md)、[セキュリティ・プライバシー仕様](../security-privacy-specs/README.md)、[法的文書仕様](../legal-specs/README.md)、[ログ・診断・品質監視仕様](../observability-specs/README.md)

## 1. 目的

MATAのビルド識別、署名、テストトラック、Google Play掲載、法的文書、段階公開、監視、停止および保守を再現可能な手順として定義する。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [バージョン・ビルド・署名仕様](versioning-build-and-signing.md) | versionName、versionCode、AAB、署名、成果物 |
| [Google Play掲載・公開仕様](play-listing-and-rollout.md) | 対象地域、掲載情報、トラック、段階公開、法的URL |
| [Google Play Console申請シート](play-console-submission.md) | App content、商品、権限、対象ユーザー、入力値、公開ブロッカー |
| [Google Playストア掲載文・画像仕様](store-listing-copy-and-assets.md) | 確定掲載文、リリースノート、画像寸法、撮影構成 |
| [Google Play Data safety申告案](data-safety-declaration.md) | 収集・共有データ、目的、削除、最終検証 |
| [リリースチェックリスト](release-checklist.md) | 準備、検証、公開、監視、停止、Hotfix |
| [Release事前検査仕様](release-preflight.md) | 設定、法的文書、ストア成果物、AABと証跡の統合検査 |
| [リリース記録仕様](release-records.md) | Release候補、試験、承認、Google Play trackおよび公開結果の追跡 |

## 3. 基本方針

1. Google Playを唯一の一般配布経路とし、ReleaseはAndroid App Bundleで公開する。
2. `com.mochisofts.mata`、署名系統、商品ID、バックアップ互換性を公開後に変更しない。
3. 同じソースコミットから、CIで検証した成果物をそのまま昇格させる。
4. テストを通していないローカルビルドを本番へアップロードしない。
5. データ移行、通知、購入権利、プライバシーに重大な未解決事項がある場合は公開しない。
6. 初回公開前は内部・クローズドテストで検証し、更新は段階公開する。
7. 公開後に問題があればロールアウトを停止し、versionCodeを上げた修正版を公開する。既存版へダウングレードしない。

## 4. 固定する公開値

| 項目 | 値 |
| --- | --- |
| アプリ名 | MATA |
| Application ID | `com.mochisofts.mata` |
| 主言語 | 日本語（`ja-JP`） |
| 初期配布地域 | 日本 |
| Google Playカテゴリ | 仕事効率化 |
| 価格 | 無料 |
| アプリ内商品 | 広告削除の買い切り `remove_ads` |
| プライバシーポリシー | `https://mochisofts.com/mata/privacy` |
| 利用規約 | `https://mochisofts.com/mata/terms` |
| 特定商取引法に基づく表記 | `https://mochisofts.com/mata/commercial-transactions` |
| 外部送信に関する公表 | `https://mochisofts.com/mata/external-transmission` |
| デベロッパーWebサイト | `https://mochisofts.com/` |
| app-ads.txt | `https://mochisofts.com/app-ads.txt` |
| アプリ提供者 | Mochisofts（個人運営） |
| Play掲載・法的文書用連絡先 | `com.mochisofts@gmail.com` |
| Web公開方式 | GitHub Pagesによる認証不要の静的サイト |

連絡先とWebサイトはGoogle Play掲載および法的文書に使用する。設定画面には既存仕様どおり「開発者Webサイト」「お問い合わせ」の行を追加しない。

公開サイト用リポジトリは`Sakemotti/mochisofts.github.io`を候補とし、リポジトリ作成、DNS、HTTPSおよびHTML公開は別のリリース準備作業とする。法的文書の氏名、住所、電話番号等のプレースホルダーが残る間は本番公開しない。

## 5. 公式資料

- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)
- [Release app updates with staged rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Use a pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
