# Google Play掲載・公開仕様

- 文書状態: 確定
- 最終更新日: 2026-09-01
- 親仕様: [リリース・配布運用仕様](README.md)

## 1. ストア掲載

- 既定言語は日本語とし、機械翻訳だけの他言語掲載を行わない。
- アプリ名は「MATA」とする。
- 短い説明と詳細説明は、繰り返しTODO、アプリ共通の一日の終わり、通知、ウィジェット、履歴、手動バックアップを実装済みの範囲で説明する。
- 未実装機能、クラウド同期、AI、共有、完全な時刻保証を表現しない。
- 広告を含むことと、買い切りで広告を削除できることを明示する。
- スクリーンショットは実際のRelease相当UIと日本語の架空データを使用し、個人情報やデバッグ表示を含めない。
- スマートフォンとタブレットの必要な画像を用意し、表示内容を同じリリースに一致させる。
- 高解像度アイコンとFeature Graphicはデザインシステムのブランド表現に従う。
- Google Playへ転記する確定文案、リリースノート、画像寸法、撮影順および代替テキストは[Google Playストア掲載文・画像仕様](store-listing-copy-and-assets.md)に従う。

## 2. 対象ユーザーとコンテンツ

- 初期配布地域は日本だけとする。
- 13歳以上の一般ユーザーを対象とし、子ども向けアプリとして設計・申告しない。
- 広告カテゴリ、クリエイティブ、対象年齢をGoogle Playのコンテンツレーティングと整合させる。
- コンテンツレーティング質問票は実際のTODO自由入力、広告、購入、外部リンクに基づいて回答する。
- アカウントとログインは不要と申告する。
- App Accessの審査用アカウントは不要とし、審査者が全基本画面を利用できるようにする。

## 3. 法的・公開URL

- プライバシーポリシーは`https://mochisofts.com/mata/privacy`で公開する。
- 利用規約は`https://mochisofts.com/mata/terms`で公開する。
- 特定商取引法に基づく表記は`https://mochisofts.com/mata/commercial-transactions`で公開する。
- 外部送信に関する公表は`https://mochisofts.com/mata/external-transmission`で公開する。
- 4文書はHTTPS、認証不要、地域制限なし、モバイル表示対応とし、本文表示にJavaScriptを必須とせずPDFだけで公開しない。
- アプリ内の設定画面とGoogle Play掲載から同じポリシーURLを開く。
- アプリ提供者はMochisofts（個人運営）、デベロッパーWebサイトは`https://mochisofts.com/`、連絡先は`com.mochisofts@gmail.com`とする。
- Webサイトは`mochisofts.com`をカスタムドメインとするGitHub Pagesの静的サイトとし、初期公開では独自のアクセス解析、広告および問い合わせフォームを設けない。
- `https://mochisofts.com/app-ads.txt`をAdMobの正式な内容で公開する。
- 特定商取引法上の請求時開示方法を含め、特定商取引法に基づく表記、利用規約および外部送信に関する公表の専門家確認を完了する。
- 文書やapp-ads.txtの実際の公開、DNS、HTTPSおよびリンク検査は本番リリース前の必須作業とし、URLを決めただけで完了扱いにしない。

## 4. Google Play申告

- Data safetyをAdMob、UMP、Billingを含むReleaseの実際のデータ処理へ一致させる。
- Adsは「広告を含む」と申告する。
- App content、Target audience、Content rating、Data safety、Privacy policy、Permissions declarationをすべて完了する。
- `SCHEDULE_EXACT_ALARM`の利用目的を時刻指定TODO通知として説明し、公開時点のPlayポリシー適格性を再確認する。
- SDK Indexの警告、Target API、Billing、広告SDKの期限を公開候補ごとに確認する。
- Play Integrity APIは初回公開では導入しない。
- 各App contentカード、Advertising ID、権限説明、アプリ内商品および初回公開ブロッカーは[Google Play Console申請シート](play-console-submission.md)に従う。
- Data safetyは[Google Play Data safety申告案](data-safety-declaration.md)をRelease AABと実通信で検証してから送信する。

## 5. テストトラック

### 5.1 Internal testing

- すべての公開候補を最初にInternal testingへ配布する。
- インストール、更新、購入、広告、通知、ウィジェット、バックアップ、復元を実Google Play環境で確認する。
- Pre-launch reportを確認する。

### 5.2 Closed testing

- 初回公開前と重大変更時はClosed testingを実施する。
- Google Playアカウント種別に応じた現行の本番アクセス要件を満たす。
- テスターから再現手順、端末、OS、versionCodeを含む結果を収集するが、TODO内容の提出を要求しない。

### 5.3 Open testing

- 初回公開では必須としない。
- 大規模な互換性確認が必要な場合だけ使用し、ストア掲載とプライバシー文書を本番相当へ整える。

## 6. 本番公開

- 初回本番公開には段階公開を利用できないため、内部・クローズド試験とPre-launch reportを完了してから公開する。
- 初回公開後24時間はAndroid vitals、レビュー、購入、広告、ポリシー状態を重点監視する。
- 2回目以降の更新は原則として10%から段階公開する。
- 各段階を少なくとも24時間監視し、10%→25%→50%→100%の順で手動拡大する。
- S0またはS1、Core vital悪化、データ移行失敗、購入権利誤判定があれば直ちに停止する。
- 低リスクの文言・表示修正でも、緊急性がなければ段階公開を使用する。
- 段階割合は自動で増加しない前提で、担当者と判断日時を記録する。

## 7. リリースノート

- 日本語でユーザーに影響する追加、変更、修正を簡潔に記載する。
- 内部実装、脆弱性の悪用手順、未公開機能を記載しない。
- データ移行、権限、広告、購入、通知の挙動変更は明記する。
- 「軽微な修正」だけで済ませず、主要な変更を識別できるようにする。
- versionName、versionCode、公開日、対象トラックと同じ記録へ保管する。

## 8. 公開停止と修正版

- 問題検知時は段階公開を停止し、影響範囲と回避策を評価する。
- Google Playでは配布済みユーザーを旧版へ戻せないため、versionCodeを上げた修正版を作成する。
- DBスキーマをダウングレードせず、前方修正のマイグレーションを追加する。
- 問題版で作成されたデータを修正版で安全に読めることを試験する。
- S0では必要に応じてストア公開停止、広告停止、鍵失効、ポリシー・ユーザー告知を行う。
- 修正版も最小限のInternal testingと自動ゲートを省略しない。
