# 収益化仕様

- 文書状態: 確定
- 最終更新日: 2026-08-27
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[TODO一覧画面仕様](../../screen-specs/todo-list/spec.md)、[設定画面仕様](../../screen-specs/settings/spec.md)、[手動バックアップ仕様](../backup-specs/README.md)、[アプリ起動・初期化・復帰仕様](../../non-functional-specs/startup-specs/README.md)、[法的文書仕様](../../non-functional-specs/legal-specs/README.md)

## 1. 目的

MATAの広告表示、広告削除購入、購入状態の復元、同意管理、プライバシー対応、試験および公開準備に関する共通仕様を定義する。

TODO、カテゴリ、履歴などの基本機能は購入状態にかかわらず同一とし、収益化機能によってユーザーデータの利用範囲を広げない。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [課金・権利状態仕様](billing-and-entitlement.md) | Google Play商品、購入、承認、復元、払い戻し、端末内状態 |
| [広告・同意管理仕様](ads-and-consent.md) | AdMobバナー、表示条件、ライフサイクル、UMP、プライバシー |
| [試験・公開仕様](testing-and-release.md) | 単体試験、UI試験、Google Play試験、広告試験、公開判定 |

## 3. 収益化モデル

1. アプリは広告付きで無料提供する。
2. Google Playの買い切り商品を購入すると、アプリ内のすべての広告を恒久的に削除する。
3. 購入の有無による機能制限は設けない。
4. サブスクリプション、消費型商品、寄付、機能追加型のアプリ内商品は設けない。
5. Google Play以外の決済、外部決済への誘導、外部購入リンクは設けない。
6. 全画面の購入催促や、操作を妨げる購入導線は設けない。

## 4. 採用サービス

| 用途 | サービス |
| --- | --- |
| 広告削除購入 | Google Play Billing |
| 広告 | Google AdMob |
| 広告に関する同意管理 | Google User Messaging Platform（UMP） |

- 各SDKは、実装時点でGoogleがサポートする最新の安定版を使用する。
- 初期リリースではMATA独自の課金検証サーバーを設けない。
- 広告メディエーションおよび他社広告SDKは使用しない。

## 5. プライバシー原則

- TODOのタイトル、説明、カテゴリ、履歴、通知内容などを広告、同意管理、課金の各サービスへ送信しない。
- 広告リクエストへTODO由来のキーワード、コンテンツ情報、カテゴリ情報を付与しない。
- 広告SDKが自動収集し得る情報は、プライバシーポリシーとGoogle Playのデータセーフティへ正しく申告する。
- 広告への同意を拒否しても、アプリの基本機能を制限しない。

## 6. 仕様変更条件

次の場合は本仕様を見直し、実装前に再承認する。

- 広告削除以外の有料機能を追加する場合
- サブスクリプションまたは消費型商品を追加する場合
- 広告形式、広告掲載画面、広告事業者またはメディエーションを変更する場合
- 独自アカウント、課金検証サーバー、Google Play Developer API、Real-time Developer Notificationsを導入する場合
- 子ども向け配信またはGoogle Playのファミリー向けプログラムを対象とする場合

## 7. 公式資料

- [Google Play Billingをアプリへ統合する](https://developer.android.com/google/play/billing/integrate.html)
- [Google Play Billingのセキュリティ](https://developer.android.com/google/play/billing/security)
- [Google Play Billingをテストする](https://developer.android.com/google/play/billing/test)
- [Google Mobile Ads SDK Next-Genの導入](https://developers.google.com/admob/android/next-gen/quick-start)
- [Google Mobile Ads SDK Next-Genのバナー広告](https://developers.google.com/admob/android/next-gen/banner)
- [User Messaging Platform](https://developers.google.com/admob/android/privacy)
- [Google Mobile Ads SDKのデータ開示](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [AdMobのapp-ads.txt](https://support.google.com/admob/answer/9363762)
