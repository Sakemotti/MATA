# 初回公開・外部SDK／通信／データ整合レビュー

- 実施日: 2026-09-07
- 対象: `REL-009`、`REL-017`
- 対象物: versionCode 1のRelease構成
- 実施者: RELEASE_OWNER
- 継続検査: `node tools/test-specs/verify-external-data-contract.mjs`

## 結論

2項目とも合格とする。Release実装、直接依存、SBOM、マージ済みManifest、Google公式資料、Google Play申請資料、Data safety、ストア掲載文、法的文書正本および公開ページの間に、申告へ影響する差異はなかった。

公開候補端末での通信キャプチャ、Play ConsoleのSDK Index・ポリシー警告およびData safetyプレビューの最終確認は、公開候補を確定する`REL-021`で再実施する。

## 1. 外部SDKと直接依存

| 対象 | Release構成 | 2026-09-07の確認 | 判定 |
| --- | --- | --- | --- |
| GMA Next-Gen SDK | `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0` | Google公式の最新版は1.4.0。自動収集・共有はIPアドレス、アプリ操作、診断情報、端末・アカウント識別子 | 一致 |
| UMP | `com.google.android.ump:user-messaging-platform:4.0.0` | Google公式の最新版は4.0.0。起動時更新、必要時フォーム、広告要求可否、プライバシー設定再表示の実装を確認 | 一致 |
| その他の送信SDK | なし | Firebase Analytics、Crashlytics、独自クラッシュ送信、Billing、他社広告SDK、メディエーションAdapterは直接依存にない | 一致 |
| アプリ内購入 | なし | Billing依存、商品、価格、購入・復元導線はない | 一致 |

アプリの直接runtime依存29件を固定リストと照合する。追加・削除が発生した場合は、CIが失敗して本レビュー、Data safetyおよび法的文書の再確認を要求する。

2026-09-09追補: `kotlinx-coroutines-core:1.11.0`をテスト用ライブラリと同一バージョンへ揃えるため直接依存へ追加した。このライブラリはアプリ内の非同期処理を提供し、独自の外部通信、データ収集またはデータ共有を追加しないため、Data safetyおよび法的文書の申告変更は不要と判定した。

## 2. 権限とManifest

アプリ自身が宣言する権限は次の5件で、Console申請シートと一致する。

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`

Releaseのマージ済みManifestでは、SDK・WorkManager等から`READ_BASIC_PHONE_STATE`、`AD_ID`、`WAKE_LOCK`、`FOREGROUND_SERVICE`およびアプリ固有の動的Receiver保護権限が追加される。既存の`verifyReleaseManifestSecurity`が許可リストとの差分とexported component差分を拒否する。ストレージ、連絡先、カメラ、マイクおよび位置情報権限はない。

## 3. 通信経路と送信内容

| 経路 | 実装上の送信内容 | 利用目的 | 選択・停止方法 | 公表 |
| --- | --- | --- | --- | --- |
| Google Mobile Ads | Google公式開示の4種類。TODO・カテゴリ本文を広告リクエストへ設定するコードはない | バナー広告、測定、不正防止 | UMPの選択肢、端末の広告設定 | Data safety、プライバシーポリシー、外部送信公表、ストア掲載に記載 |
| UMP | 地域判定、端末・アプリ情報、同意・選択状態等 | 同意・選択肢管理 | 必要な地域では設定画面から再表示 | プライバシーポリシー、外部送信公表に記載 |
| Holidays JP | HTTPS GET、固定User-Agent `MATA`、ETag、Last-Modified。本文なし | 日本の祝日取得 | ネットワークを無効化すると送信停止。更新不可の可能性を記載 | Data safety、プライバシーポリシー、外部送信公表に記載 |
| GitHub Pages | 外部ブラウザーが法的ページを閲覧する際の通常のWeb通信 | 法的文書の表示 | ページを開かなければ送信されない | プライバシーポリシー、外部送信公表に記載 |
| Google Play / Android vitals | Google Play・OS側の配布、クラッシュ、ANR、診断 | 配布、品質、安全性 | Google Playおよび端末設定 | プライバシーポリシー、外部送信公表に記載 |

MATAが直接保持する固定HTTPS通信先は`https://holidays-jp.github.io/api/v1/date.json`の1件だけである。法的文書URLは`mochisofts.com`の承認済みpathだけを外部ブラウザーへ渡す。広告通信はGMA／UMP SDKがGoogleの管理する送信先へ行い、任意のURLを入力・送信する機能はない。

広告・祝日実装はTODO、カテゴリ、履歴、バックアップのモデルまたは自由入力欄を参照しない。Logcatは固定イベントコードと数値・列挙値だけを受け付け、自由入力またはSDKレスポンス本文を記録しない。

## 4. 文書・公開状態の照合

- Google Play申請シート: 「広告あり」「広告IDあり」「アプリ内購入なし」「アカウントなし」と実装が一致。
- Data safety: おおよその位置、アプリ操作、診断情報、デバイスまたはその他のIDを収集・共有として申告。TODO等は端末外へ自動送信しない扱いで一致。
- ストア掲載文: 広告付き無料、アプリ内購入なし、端末内保存、手動バックアップの記載が一致。
- UMP: 同意情報更新前に広告を要求せず、`canRequestAds()`が真になってからSDKを初期化する。必要時だけ設定画面へプライバシー設定を表示する。
- 公開ページ: 2026年9月7日にプライバシーポリシー、外部送信公表、利用規約、`app-ads.txt`がHTTPS 200であることを確認。
- 同期状態: 上記4公開物は改行差を正規化した内容が`legal-site`正本と完全一致。

## 5. 参照した公式資料

- [GMA Next-Gen SDK](https://developers.google.com/ad-manager/mobile-ads-sdk/android/next-gen/sdk)
- [GMA Next-Gen SDKのGoogle Playデータ開示](https://developers.google.com/ad-manager/mobile-ads-sdk/android/next-gen/privacy/play-data-disclosure)
- [GMA Next-Gen SDKのプライバシー設定](https://developers.google.com/ad-manager/mobile-ads-sdk/android/next-gen/privacy/strategies)
- [UMP SDKの設定](https://developers.google.com/admob/android/privacy)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Holidays JP API](https://github.com/holidays-jp/holidays-jp.github.io)

## 6. 実行した検査

```text
node tools/test-specs/verify-external-data-contract.mjs
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache
./gradlew :app:lintRelease :app:generateReleaseArtifactMetadata :app:verifyReleaseManifestSecurity --no-daemon --no-configuration-cache
```

Pull Request CIで同じ静的検査、Release SBOM・Manifest検査、Debug／Release／PerformanceビルドおよびAPI 30 instrumented testが成功した時点で最終証跡とする。
