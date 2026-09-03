# Google Play Console申請シート

- 文書状態: 入力値確定・外部設定未完了
- 最終更新日: 2026-09-02
- 親仕様: [リリース・配布運用仕様](README.md)
- 関連文書: [ストア掲載文・画像仕様](store-listing-copy-and-assets.md)、[Data safety申告案](data-safety-declaration.md)、[法的文書仕様](../legal-specs/README.md)

## 1. 用途

初回公開時にGoogle Play Consoleへ入力する値と回答方針を一元化する。項目名や選択肢は変更される可能性があるため、実際のConsoleに表示された現行文言へ読み替える。公開候補の実装またはGoogleの公式要件と異なる場合は、Consoleの現行要件を確認したうえで本書を更新する。

## 2. アプリ作成・ストア設定

| 項目 | 入力値 |
| --- | --- |
| アプリ名 | MATA |
| デフォルト言語 | 日本語（日本）`ja-JP` |
| アプリまたはゲーム | アプリ |
| 無料または有料 | 無料 |
| Application ID | `com.mochisofts.mata` |
| カテゴリ | 仕事効率化 |
| タグ候補 | To-do list、Task management、Personal organizerに相当する選択肢がConsoleに存在する場合だけ選ぶ |
| 初期配布国・地域 | 日本 |
| 対応フォームファクタ | Androidスマートフォン、タブレット、Chromebook |
| 対象外 | Wear OS、Android TV、Android Auto / Automotive OS、Android XR |
| 連絡先メール | `com.mochisofts@gmail.com` |
| Webサイト | `https://mochisofts.com/` |
| プライバシーポリシー | `https://mochisofts.com/mata/privacy` |

- 無料アプリは公開後に有料アプリへ戻せない前提で、アプリ本体を無料のまま維持する。
- 初期公開ではアプリ内商品または有料アプリを登録しない。将来有料化する場合は、販売者情報の公開範囲を確認してから別途判断する。
- ストア掲載文と画像は[ストア掲載文・画像仕様](store-listing-copy-and-assets.md)を転記する。

## 3. App content回答

| 項目 | 回答 | 補足 |
| --- | --- | --- |
| プライバシーポリシー | 上記URL | アプリ内の設定画面からも同じURLを開く。公開済みHTTPSページでなければ申請しない。 |
| 広告 | はい、広告を含む | TODO一覧、カテゴリ別TODO一覧、ウィジェット操作専用画面下部のAdMobバナー。 |
| アプリへのアクセス / Sign-in details | すべての機能を特別なアクセスなしで利用できる | MATA独自ログイン、会員資格、位置制限はない。テスト認証情報は不要。 |
| 対象ユーザー | 13～15歳、16～17歳、18歳以上 | 12歳以下を選択せず、子ども向けまたはFamilies対象として申告しない。 |
| ニュースアプリ | いいえ | ニュース配信機能はない。 |
| 政府関連アプリ | いいえ | 政府情報の提供または政府機関の代理ではない。 |
| Financial features | My app doesn't provide any financial features | 決済、金融商品その他の金融機能を提供しない。 |
| Health apps | My app doesn't provide any health features | 健康データ、運動、医療、ウェルネスの記録・助言機能はない。 |
| 広告ID | はい | GMA Next-Gen SDKが`AD_ID`を使用し得る。目的は広告またはマーケティング、分析、詐欺防止・セキュリティ・コンプライアンス。 |
| アカウント作成・削除 | アカウントを作成できない | Google PlayアカウントはMATA独自アカウントではない。アカウント削除URLは不要。 |
| Data safety | [Data safety申告案](data-safety-declaration.md)のとおり | Release AABと実通信の確認後に送信する。 |

Consoleに追加の宣言カードが表示された場合は、表示を無視せず、公開候補の実装を根拠に回答と証跡を本書へ追加する。

## 4. コンテンツレーティング回答案

IARC質問票はConsoleに表示される実際の質問へ正確に回答し、GoogleまたはIARCが発行した結果を採用する。次は初回公開機能に基づく回答案であり、レーティング値を事前に断定しない。

| 質問分野 | 回答案 | 根拠 |
| --- | --- | --- |
| 暴力、恐怖、性的表現、賭博、薬物、差別、犯罪、強い言葉 | すべて「なし」 | アプリがこれらのコンテンツを提供しない。 |
| ユーザー間の交流・通信 | なし | アカウント、共有、投稿、チャットがない。 |
| ユーザー生成コンテンツの公開・共有 | なし | 自由入力TODOは端末内だけで、他の利用者へ公開されない。 |
| 位置情報の共有 | なし | 位置情報機能や他ユーザーへの共有がない。広告SDKのデータ処理はData safetyで申告する。 |
| 無制限のインターネットアクセス | なし | 任意URLを閲覧するブラウザーではなく、固定された法的ページだけを外部ブラウザーで開く。 |
| デジタル商品の購入 | なし | 有料アプリ、アプリ内商品、サブスクリプションまたは寄付を提供しない。 |
| 広告 | あり | AdMobバナーを表示する。 |

- IARC結果とAdMobの広告コンテンツ上限・カテゴリブロックを整合させる。
- 成人向け、ギャンブル、出会い系および過度に刺激的な広告カテゴリを公開前にブロックする。
- アプリ内容を変更して質問票回答へ影響する場合は再提出する。

## 5. 権限・APIの申告

Releaseのマージ済みManifestを正とする。現在の承認済み権限と申告方針は次のとおり。

| 権限・API | 用途 | Console対応 |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | 期限前・期限超過通知と通知操作 | 実行時に説明して要求する。通常の高リスク権限申告フォームは不要。 |
| `SCHEDULE_EXACT_ALARM` | 利用者が時刻指定したTODO通知 | `USE_EXACT_ALARM`は使用しない。Consoleが説明を要求した場合は下記文面を使用する。 |
| `RECEIVE_BOOT_COMPLETED` | 再起動後の通知・ウィジェット再構成 | 通知・ウィジェットの復元だけに使用する。 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 祝日、広告、同意、法的ページ | Data safetyとプライバシーポリシーへ反映する。 |
| `com.google.android.gms.permission.AD_ID` | AdMob広告、測定、不正防止 | Advertising IDを「使用する」と申告する。 |
| SDK由来の`READ_BASIC_PHONE_STATE`、`FOREGROUND_SERVICE`、`WAKE_LOCK`等 | 広告SDK、WorkManager等のライブラリ動作 | Release AABのManifestとSDK Indexで必要性を確認し、不要な権限が追加された場合は除外または仕様更新する。 |

正確なアラームについて説明を求められた場合の文案:

> MATAは、ユーザーがTODOごとに指定した期限時刻、事前通知時刻および期限超過通知時刻にリマインダーを表示するTODO管理アプリです。SCHEDULE_EXACT_ALARMは、ユーザーが明示的に設定した時刻に近いタイミングで通知するためだけに使用します。権限がない場合もアプリの基本機能は利用でき、設定画面から権限状態と代替動作を確認できます。

## 6. 有料機能

- 初期公開では有料アプリ、アプリ内商品、サブスクリプション、寄付および広告削除機能を登録しない。
- アプリ内に価格、購入、購入状況の復元または外部決済への導線を設けない。
- 有料機能を追加する場合は、販売者情報の公開、法的文書、税務、Data safetyおよびGoogle Playの現行ポリシーを実装前に再確認する。

## 7. リリースとテストトラック

| 項目 | 初期値・方針 |
| --- | --- |
| Version name | `1.0.0` |
| Version code | `1`。未使用であることをConsoleで確認する。 |
| Target API | 36 |
| 配布形式 | Android App Bundle（AAB） |
| App signing | Play App Signingを有効化し、Upload KeyでAABへ署名する。 |
| 最初のトラック | Internal testing |
| 次のトラック | Closed testing |
| Open testing | 初回公開では任意。Production access取得後に必要な場合だけ使用する。 |
| Production | Internal、Closed、Pre-launch report、法的公開およびリリースゲート完了後 |

- 2026年8月31日以降に新規アプリへ要求されるTarget API 36を満たす構成とするが、申請日に現行要件を再確認する。
- 2026年9月30日から適用されるPlay Console要件を含め、デベロッパーの法的名称、住所、連絡先、支払いプロファイルおよびアプリ情報を正確かつ最新の状態に保つ。
- 個人デベロッパーアカウントが2023年11月13日以降に作成されている場合、12人以上が14日間継続参加するClosed testを完了し、Production accessを申請する。
- アカウント作成日が条件以前の場合も、初回公開前にClosed testを実施する。
- Internal testingへ実AABを配布してから、広告、UMP、通知、ウィジェット、バックアップおよび法的リンクをGoogle Play環境で確認する。

## 8. 初回公開前ブロッカー

- [x] Google Play個人デベロッパーアカウントの本人確認、連絡先メールおよび電話番号の確認を完了した。
- [ ] Play Consoleに表示される本番アクセス要件を確認し、必要なテスト要件を満たした。
- [ ] `mochisofts.com`でプライバシーポリシー等3文書と`app-ads.txt`を公開した。
- [x] AdMobの正式なApp ID、バナー広告ユニットID、Publisher IDおよびUMPメッセージを設定した。
- [ ] 512pxストアアイコン、フィーチャーグラフィック、スマートフォン6枚、タブレット各4枚の画像を作成した。
- [ ] Data safety、Advertising ID、Ads、Target audience、Content rating、Financial features、Health apps等の全カードを送信した。
- [ ] アプリ内商品が未登録で、アプリとストア掲載に購入導線がないことを確認した。
- [ ] Upload Keyを安全に作成・保管し、Play App Signingを設定した。
- [ ] アカウント条件に該当する場合、12人・14日間のClosed testとProduction access申請を完了した。
- [ ] Release AAB、Pre-launch report、全P0/P1試験、SDK Indexおよびポリシー状態が合格した。

## 9. 公式資料

- [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Manage target audience and app content settings](https://support.google.com/googleplay/android-developer/answer/9867159)
- [Content rating requirements](https://support.google.com/googleplay/android-developer/answer/9859655)
- [Advertising ID](https://support.google.com/googleplay/android-developer/answer/6048248)
- [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271)
- [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Play Console requirements](https://support.google.com/googleplay/android-developer/answer/10788890)
