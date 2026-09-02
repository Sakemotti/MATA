# アプリ・通信セキュリティ仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 親仕様: [セキュリティ・プライバシー仕様](README.md)

## 1. 端末内保存

- Room DB、DataStore、キャッシュ、一時ファイルはアプリ専用の内部ストレージへ保存する。
- world-readableまたはworld-writableなファイルを作成しない。
- DB、DataStore、キャッシュをContentProviderで公開しない。
- 通常データを外部ストレージの固定パスへ直接保存しない。
- 端末のファイルベース暗号化と画面ロックを基礎とし、アプリ独自暗号化は初回公開では追加しない。
- 機密性の高い秘密鍵を端末内へ保存する必要が生じた場合はAndroid Keystoreを使用し、別仕様を策定する。

## 2. バックアップ除外

- Manifestで`allowBackup=false`を設定する。
- 対応OS向けの`dataExtractionRules`と`fullBackupContent`でもクラウドバックアップと端末間転送を除外する。
- Debug、Releaseの両Manifest統合結果で除外設定を確認する。
- Room、DataStore、通知内部状態、一時ファイルをOS自動バックアップへ含めない。

## 3. 権限

初回公開で使用を許可する権限・特別アクセスは次に限定する。

- `INTERNET`: 祝日、広告、同意
- `ACCESS_NETWORK_STATE`: 通信可否とSDK要件
- `POST_NOTIFICATIONS`: Android 13以上の通知
- `SCHEDULE_EXACT_ALARM`: ユーザーが時刻指定したTODO通知
- `RECEIVE_BOOT_COMPLETED`: 再起動後の通知・ウィジェット整合
- App WidgetとSDKがManifest統合で必要とする通常権限

- `USE_EXACT_ALARM`は使用しない。
- ストレージ全体、写真・動画、位置情報、連絡先、マイク、カメラ、電話、SMS、カレンダーの権限を要求しない。
- 権限は必要になる文脈で説明して要求し、拒否しても端末内基本機能を継続する。
- 依存SDKが追加する権限をManifest統合結果で検査し、不要な権限は削除する。

## 4. Androidコンポーネント

- Launcher ActivityだけをランチャーIntent Filterに必要な範囲でexportedとする。
- 完了操作Receiver、Worker補助Receiver、内部Service、Providerは`exported=false`とする。
- OSから受信が必要なApp Widget Providerや起動Receiverは、許可されたActionだけを受け付ける。
- 外部起動Intentは明示したAction、型、ID、範囲を検証し、不正値を無視して安全な初期画面へ移動する。
- 汎用の暗黙IntentでTODO IDや操作命令を受け付けない。
- 外部App Link、カスタムURIスキーム、他アプリ向け共有Providerは初回公開では設けない。

## 5. PendingIntent

- 可能な限り明示的かつ`FLAG_IMMUTABLE`とする。
- RemoteViews等で可変性が技術的に必要な箇所だけ`FLAG_MUTABLE`を使用し、対象コンポーネント、テンプレート、入力範囲を限定する。
- 用途、TODO ID、予定キー、定義リビジョンから衝突しない識別子を生成する。
- Intent内のタイトル、状態、カテゴリを信頼せず、Roomから最新値を再取得する。
- 受信時に対象存在、現在期間、状態、操作可否をトランザクション内で再検証する。

## 6. 通信

- Network Security ConfigurationでCleartext Trafficを禁止する。
- HTTPS URLだけを許可し、リダイレクト後もHTTPSと許可先を検証する。
- ReleaseはシステムCAを信頼し、ユーザー追加CAを信頼しない標準設定とする。
- Debug専用のCAを使用する場合は`debug-overrides`へ限定し、Releaseへ含めない。
- 証明書ピンニングは証明書更新による停止リスクが高いため使用しない。
- ホスト名検証、TLS証明書検証、SDKのセキュリティ既定値を無効化しない。
- Cookie、認証ヘッダー、TODO内容を祝日API要求へ付与しない。
- 応答サイズ、Content-Type、文字コード、JSON構造、日付範囲を祝日仕様に従って検証する。

## 7. 入力とファイル

- 文字数、数値範囲、日付範囲、カテゴリ参照をUIと永続化境界の両方で検証する。
- SQLはRoomの引数バインドを使用し、ユーザー入力をSQL文字列へ連結しない。
- バックアップはZIP Slip、Zip Bomb、重複名、未知エントリ、サイズ超過、参照不整合を拒否する。
- SAFから受け取った表示名、MIME、URIを信頼せず、実内容を検証する。
- HTMLまたはWebViewへユーザー入力を挿入しない。WebViewは初回公開では使用しない。
- ライセンスやポリシーを外部ブラウザーで開くURLはアプリ固定のHTTPS URLだけを使用する。

## 8. 秘密情報と署名

- リリース署名鍵、keystoreパスワード、サービスアカウント、CIトークンをGitへコミットしない。
- 秘密情報はCI Secretまたは開発者の保護されたローカル設定から注入する。
- APKから取得できる値を秘密として扱わない。APIキーを置く場合はAPI、アプリID、署名証明書で制限する。
- AdMob App IDと広告ユニットIDは識別子であり秘密情報ではないが、DebugとReleaseを分離する。
- Release署名はGoogle Play App Signingを使用し、Upload Keyを別管理する。
- Debug署名鍵でRelease成果物を署名できないようビルドを分離する。

## 9. 依存関係

- Google Maven、Maven Central、Gradle Plugin Portalなど承認済みの公式配布元だけを使用する。
- 動的バージョン、SNAPSHOT、未固定Git依存をReleaseへ使用しない。
- Gradle Dependency Verificationと依存関係ロックを有効にする。
- Dependabotで週次確認し、重大・悪用確認済み脆弱性を優先して更新する。
- SDK追加時は権限、通信、データ収集、初期化、サイズ、保守状況をレビューする。
- 未使用SDKと推移依存を削除する。

## 10. Releaseビルド

- Debuggableを無効にし、テスト用メニュー、Fake、テスト広告切替、詳細ログを含めない。
- R8を有効にし、難読化マッピングを安全に保管する。
- StrictModeの視覚通知や開発用サーバー設定を含めない。
- Backup、exported、Cleartext、権限、署名を最終AABの解析結果で検証する。
- スクリーンショット、テストデータ、ローカルパス、資格情報を成果物へ含めない。
