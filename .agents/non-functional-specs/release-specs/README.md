# リリース・配布運用仕様

- 文書状態: 確定
- 最終更新日: 2026-09-17
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[開発ガイドライン](../../development-guidelines.md)、[総合動作確認項目書](../../test-specs/README.md)、[収益化仕様](../../functional-specs/monetization-specs/README.md)、[セキュリティ・プライバシー仕様](../security-privacy-specs/README.md)、[法的文書仕様](../legal-specs/README.md)、[ログ・診断・品質監視仕様](../observability-specs/README.md)

## 1. 目的

MATAのビルド識別、署名、テストトラック、Google Play掲載、法的文書、段階公開、監視、停止および保守を再現可能な手順として定義する。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [バージョン・ビルド・署名仕様](versioning-build-and-signing.md) | versionName、versionCode、AAB、署名、成果物 |
| [Google Play掲載・公開仕様](play-listing-and-rollout.md) | 対象地域、掲載情報、トラック、段階公開、法的URL |
| [Google Play Console申請シート](play-console-submission.md) | App content、権限、対象ユーザー、入力値、公開ブロッカー |
| [Google Playストア掲載文・画像仕様](store-listing-copy-and-assets.md) | 確定掲載文、リリースノート、画像寸法、撮影構成 |
| [Google Play Data safety申告](data-safety-declaration.md) | 収集・共有データ、目的、削除、最終検証 |
| [リリースチェックリスト](release-checklist.md) | 準備、検証、公開、監視、停止、Hotfix |
| [Release事前検査仕様](release-preflight.md) | 設定、法的文書、ストア成果物、AABと証跡の統合検査 |
| [初回リリース進行記録](initial-release-status.md) | `1.0.0 (5)`のClosed testingと、`1.0.0 (6)`本番公開候補の計画、Console、実機確認、保留事項 |
| [Production access申請回答・実施票](production-access-application-draft.md) | Closed testing、対象ユーザー、本番準備状況の提出用回答と申請・審査記録 |

## 3. 基本方針

1. Google Playを唯一の一般配布経路とし、ReleaseはAndroid App Bundleで公開する。
2. `com.mochisofts.mata`、署名系統、バックアップ互換性を公開後に変更しない。
3. 同じソースコミットから、CIで検証した成果物をそのまま昇格させる。
4. テストを通していないローカルビルドを本番へアップロードしない。
5. データ移行、通知、広告同意、プライバシーに重大な未解決事項がある場合は公開しない。
6. 初回公開前は内部・クローズドテストで検証し、更新は段階公開する。
7. 公開後に問題があればロールアウトを停止し、versionCodeを上げた修正版を公開する。既存版へダウングレードしない。

### 3.1 候補の呼称

- `Closed testing候補`: Closed testingへ配布するため、署名とRelease事前検査に合格した成果物。本番公開の承認を意味しない。
- `本番公開候補`: Closed testing完了後にProduction向けとして改めて固定し、全公開ゲートの対象とする成果物。
- 初回公開ではversionCode `5`をClosed testing検証版、versionCode `6`を本番公開候補として扱い、versionCode `5`をProductionへ昇格しない。
- versionCode `6`はClosed testing要件達成後にクリーンなmainから生成し、実績を[本番公開候補生成計画](../../test-specs/production-release-candidate-1.0.0-6-plan.md)へ引き継ぐ。

## 4. 固定する公開値

| 項目 | 値 |
| --- | --- |
| アプリ名 | MATA |
| Application ID | `com.mochisofts.mata` |
| 主言語 | 日本語（`ja-JP`） |
| 初期配布地域 | 日本 |
| Google Playカテゴリ | 仕事効率化 |
| 価格 | 無料 |
| アプリ内商品 | なし |
| プライバシーポリシー | `https://mochisofts.com/mata/privacy` |
| 利用規約 | `https://mochisofts.com/mata/terms` |
| 外部送信に関する公表 | `https://mochisofts.com/mata/external-transmission` |
| デベロッパーWebサイト | `https://mochisofts.com/` |
| app-ads.txt | `https://mochisofts.com/app-ads.txt` |
| アプリ提供者 | Mochisofts（個人運営） |
| Play掲載・法的文書用連絡先 | `com.mochisofts@gmail.com` |
| Web公開方式 | GitHub Pagesによる認証不要の静的サイト |

連絡先とWebサイトはGoogle Play掲載および法的文書に使用する。設定画面には既存仕様どおり「開発者Webサイト」「お問い合わせ」の行を追加しない。

GitHub Pages用の公開リポジトリ、DNS、カスタムドメインおよびHTTPSは設定済みである。本リポジトリの`legal-site`を正本とし、変更を`main`へマージした後にユーザーが公開リポジトリへコピーする。設定画面から公開中のプライバシーポリシーと利用規約を開けることを確認済みとする。

## 5. 公式資料

- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)
- [Release app updates with staged rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Use a pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
