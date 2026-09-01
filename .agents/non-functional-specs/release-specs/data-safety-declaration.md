# Google Play Data safety申告案

- 文書状態: 申請案・Release実機通信確認前
- 最終更新日: 2026-09-01
- 親仕様: [リリース・配布運用仕様](README.md)
- 関連仕様: [データ・プライバシー仕様](../security-privacy-specs/data-and-privacy.md)、[外部送信に関する公表](../legal-specs/external-transmission.md)

## 1. 適用範囲

本書は、2026年9月1日時点のRelease構成をGoogle Play ConsoleのData safetyフォームへ転記するための申請案である。MATA本体だけでなく、GMA Next-Gen SDK 1.4.0、UMP 4.0.0、Google Play Billing 9.1.0およびHolidays JPへの通信を含む。GMA Next-Gen SDKの公式開示は2026年8月31日更新版を確認した。

Google Playの定義では、アプリまたはSDKが端末外へ送信するデータを「収集」に含める。最終回答はRelease AABのマージ済みManifest、Google Play SDK Index、各SDKの最新開示および実機通信検査を基に確定し、本書との差異があれば公開前に本書、プライバシーポリシーおよび外部送信に関する公表を同時更新する。

## 2. フォーム冒頭の回答案

| 質問 | 回答案 | 根拠・注意 |
| --- | --- | --- |
| アプリは必要なデータタイプを収集または共有するか | はい | 広告SDKがデータを自動収集・共有し、Billingが購入状態を扱う。 |
| 収集するユーザーデータを転送中に暗号化するか | はい | GMA Next-Gen SDKはTLSを使用し、MATAは平文HTTPを禁止する。Release通信検査で再確認する。 |
| ユーザーがアカウントを作成できるか | いいえ | MATA独自アカウント、ログイン、クラウド同期はない。Google PlayアカウントはMATAが作成するアカウントではない。 |
| アカウント削除URL | 対象外 | アカウントを作成できないため登録しない。 |
| データ削除の要求手段 | いいえ（申請時のフォーム文言を再確認） | 端末内データはアプリ内削除またはアンインストールで削除できるが、提供者はGoogle等が保持するデータを直接削除できない。提供者が削除できる外部サーバーデータがない状態で「削除要求に対応」と過大申告しない。 |
| 独立したセキュリティ審査 | いいえ | 初回公開時点で独立監査または認証を受けていない。 |

## 3. 申告するデータタイプ

「必須/任意」はGoogle Playフォーム上の選択を示す。広告削除購入は収集への無償の任意選択とは扱わず、広告付き無料利用でSDKが必要とするデータは「必須」とする。購入履歴は購入または復元を選んだ利用者だけが対象となるため「任意」とする。

| Google Playのデータタイプ | 収集 | 共有 | 一時的処理のみ | 必須/任意 | 利用目的 | 主な処理元 |
| --- | --- | --- | --- | --- | --- | --- |
| 位置情報 / おおよその位置情報 | はい | はい | いいえ | 必須 | アプリの機能、広告またはマーケティング、分析、詐欺防止・セキュリティ・コンプライアンス | GMA Next-Gen SDKがIPアドレスを利用。Holidays JPへの通常のHTTPS接続でも送信先がIPアドレスを扱う場合がある。 |
| アプリのアクティビティ / アプリの操作 | はい | はい | いいえ | 必須 | 広告またはマーケティング、分析、詐欺防止・セキュリティ・コンプライアンス | GMA Next-Gen SDKがアプリ起動、タップ、広告操作等を自動収集・共有する。 |
| アプリの情報、パフォーマンス / 診断情報 | はい | はい | いいえ | 必須 | 広告またはマーケティング、分析、詐欺防止・セキュリティ・コンプライアンス | GMA Next-Gen SDKが起動時間、ハング率、消費電力等を自動収集・共有する。 |
| デバイスまたはその他のID | はい | はい | いいえ | 必須 | 広告またはマーケティング、分析、詐欺防止・セキュリティ・コンプライアンス | 広告ID、App Set ID、Publisher first-party IDおよび該当する端末・アカウント識別子。 |
| 財務情報 / 購入履歴 | はい | いいえ | いいえ | 任意 | アプリの機能、詐欺防止・セキュリティ・コンプライアンス | Billingが商品ID、購入状態、購入トークン、数量、承認状態を処理する。Google Playは決済サービスとして扱い、カード番号等はMATAが取得しない。 |

### 3.1 「共有」の判断

- GMA Next-Gen SDKの公式開示が4種類のデータを「自動的に収集・共有する」と明示するため、広告関連4種類は共有を「はい」とする。
- Google Play Billingは利用者が開始する決済処理であり、Google Playを決済サービスとして使用する。購入履歴はMATAが受信して権利判定に使用するため収集を「はい」とするが、決済処理のためのサービスプロバイダーへの処理として共有を「いいえ」とする案とする。申請時のGoogle PlayフォームおよびBillingの最新開示が異なる場合はそちらを優先する。
- Holidays JPは祝日JSONを提供する外部サービスであり、TODO等を送らない。接続元IPの取扱いは、おおよその位置情報の保守的な申告へ含める。

## 4. 申告しないデータ

次は端末外へ自動送信しないため、初回公開のData safetyでは収集・共有へ含めない。

- TODOのタイトル、説明、実行日、期限、通知設定および履歴
- カテゴリ名、色、アイコン、並び順および一日の終了時刻
- 完了、未完了、スキップおよびアーカイブ状態
- アプリの表示設定
- 手動バックアップの本文、ファイル名および保存先URI
- 氏名、メールアドレス、電話番号、住所、連絡先
- 正確な位置情報、写真、動画、音声、健康情報
- アプリ内検索履歴、Web閲覧履歴、インストール済みアプリ一覧
- クラッシュログまたは独自利用分析イベント

手動バックアップは利用者がAndroidのシステムファイル選択画面で保存先を選ぶ利用者主導の転送であり、提供者は受信しない。サポートメールはアプリ外で利用者が任意に送信するため、本アプリ内のData safety収集には含めない。

## 5. SDK・OS処理の扱い

- UMPの同意状態と通信はGMA Next-Gen SDKの広告関連申告および外部送信公表へ含める。最新のUMP開示で追加データタイプが示された場合は追加する。
- Google PlayやOSがアプリ外の基盤として扱うAndroid vitalsは、MATAへ組み込んだ独自SDKによる収集として申告しない。独自のクラッシュ送信SDKを追加した場合は診断情報等を再判定する。
- Releaseのマージ済みManifestには広告SDK由来の`AD_ID`等が含まれる。宣言権限だけでデータタイプを決めず、実際のAPI・SDK処理と突合する。
- GMA Next-Gen SDKのPublisher first-party IDは既定で有効になり得るため、無効化しない限りデバイスまたはその他のIDへ含める。

## 6. 送信前検証

- [ ] 公開候補AABからSBOMまたは依存関係一覧を作り、未申告SDKがない。
- [ ] マージ済みManifestの権限を確認し、`AD_ID`、Billing、ネットワーク関連権限を本書と照合した。
- [ ] Google Play SDK IndexでGMA Next-Gen SDK、UMP、Billingの警告と最新開示を確認した。
- [ ] 広告同意前、同意後、同意拒否、広告削除購入後、購入復元、祝日取得の各状態で通信先と送信項目を検査した。
- [ ] TODOタイトル、説明、カテゴリ名、バックアップ本文および購入トークンが広告・祝日・ログへ含まれないことを確認した。
- [ ] Data safety、プライバシーポリシー、外部送信に関する公表、UMPメッセージおよびAdMob構成を同じ日付で照合した。
- [ ] フォーム送信後のGoogle Playプレビューを保存し、本書へ申請日と証跡を記録した。

## 7. 公式資料

- [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
- [GMA Next-Gen SDK: Google Play's data disclosure requirements](https://developers.google.com/ad-manager/mobile-ads-sdk/android/next-gen/privacy/play-data-disclosure)
- [Declare your app's data use](https://developer.android.com/privacy-and-security/declare-data-use)
- [Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
