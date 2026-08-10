# セキュリティ・プライバシー仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [アプリ全体仕様](../app-spec.md)、[開発ガイドライン](../development-guidelines.md)、[手動バックアップ仕様](../backup-specs/README.md)、[通知仕様](../notification-specs/README.md)、[収益化仕様](../monetization-specs/README.md)、[祝日情報連携仕様](../holiday-specs/README.md)

## 1. 目的

TODO、説明、カテゴリ、履歴、設定および購入状態を必要最小限の範囲で安全に扱い、外部送信、権限、端末内保存、コンポーネント公開、バックアップおよびGoogle Play申告を実装と一致させる。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [データ・プライバシー仕様](data-and-privacy.md) | データ一覧、利用目的、保持、外部送信、Data safety |
| [アプリ・通信セキュリティ仕様](application-and-network-security.md) | 保存、権限、Manifest、PendingIntent、通信、秘密情報 |
| [検証・公開判定仕様](verification-and-release.md) | 脅威確認、静的・動的検査、依存関係、公開条件 |

## 3. 基本方針

1. TODO本文と履歴は端末内だけに保存し、MATAのサーバーへ送信しない。
2. 権限、外部SDK、収集データ、通信先を機能に必要な最小限にする。
3. アプリ専用データはAndroidのアプリサンドボックスへ保存し、他アプリへ公開しない。
4. 平文HTTPを禁止し、TLSとプラットフォームの信頼ストアを使用する。
5. 外部から受け取るIntent、URI、バックアップ、SDK結果を信頼せず検証する。
6. 秘密情報、署名鍵、サービスアカウント資格情報をAPK、ソース管理、ログへ含めない。
7. セキュリティ障害時もユーザーデータを暗黙に削除せず、安全側で処理を停止する。
8. 実際のアプリと全SDKの挙動を基に、プライバシーポリシーとGoogle Play Data safetyを公開ごとに更新する。

## 4. 対象となる信頼境界

- Compose UIとViewModel
- Room、DataStore、アプリ専用ファイル
- Storage Access Frameworkでユーザーが選択したURI
- Notification、AlarmManager、WorkManager、App WidgetのPendingIntentとReceiver
- Holidays JP API
- Google Mobile Ads SDK、UMP SDK、Google Play Billing
- Google Play、OSバックアップ、端末ロック、他アプリ
- Debug・CI・署名・公開環境

## 5. 公式資料

- [Android security best practices](https://developer.android.com/privacy-and-security/security-tips)
- [Network security configuration](https://developer.android.com/privacy-and-security/security-config)
- [Improve your app security](https://developer.android.com/privacy-and-security)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play SDK Index](https://play.google.com/sdks)
