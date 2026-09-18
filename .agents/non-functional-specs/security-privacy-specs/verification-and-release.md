# セキュリティ・プライバシーの検証・公開判定仕様

- 文書状態: 確定
- 最終更新日: 2026-09-18
- 親仕様: [セキュリティ・プライバシー仕様](README.md)

## 1. 設計レビュー

機能またはSDKの追加・変更時に次を確認する。

- 扱うデータ、保存先、保持期間、通信先、利用目的
- 必要な権限、exportedコンポーネント、PendingIntent
- 信頼境界、外部入力、想定される悪用と被害
- オフライン、権限拒否、改ざん、再送、競合時の安全な動作
- プライバシーポリシー、利用規約、Data safety、同意画面への影響
- 依存SDKのGoogle Play SDK Index、保守状況、既知の問題

## 2. 自動検査

- Android LintとCompose LintをRelease設定で実行する。
- Dependabot alerts、security updatesおよび週次version updatesで既知の依存関係脆弱性と更新を検出する。
- Gradle Dependency VerificationをCIで強制する。
- GitHub secret scanningとpush protectionを有効にし、CIのSecret Scannerで鍵、トークン、keystore、サービスアカウントJSONを重ねて検出する。
- CodeQL default setupでGitHub ActionsとJavaScript/TypeScriptを解析する。Java/KotlinはCodeQLがプロジェクトのKotlin版をサポートするまで通常CIで検査し、候補生成前にCodeQL対応状況を再確認する。
- 最終Manifestから権限、exported、backup、cleartext、Providerを機械的に検査する。
- Release AABでDebuggable、テストコード、Debug URL、テスト広告IDの混入を検査する。
- Room migration、バックアップ検証、Intent検証、PendingIntent重複防止を自動試験する。

### 2.1 リポジトリ保護

`main`には次のbranch protectionを適用する。

- Pull Requestを必須とし、単独開発のため承認レビュー数は0とする。
- branchを最新の`main`へ追従させてからマージする。
- GitHub Actionsが発行する`Test, lint, and build`を必須チェックとする。
- GitHub Advanced Securityが発行する`CodeQL`を必須チェックとする。
- 管理者にも保護を適用し、通常作業で保護を迂回しない。
- 未解決のレビュー会話があるPull Requestをマージしない。
- force pushと`main`の削除を禁止する。
- 現在のmerge commit運用を維持するため、linear historyと署名commitは必須にしない。

必須チェック名または発行元GitHub Appが変わった場合は、成功中のチェックを確認してbranch protectionを更新する。チェック待機を回避する目的で保護を一時無効化せず、workflow側の`pull_request`トリガー、チェック名および実行結果を修正する。緊急修正もbranchとPull Requestを作成し、同じ必須チェックを通す。

## 3. 動的検査

- Proxyまたは端末のネットワーク検査で通信先と送信項目を確認する。
- TODOタイトル、説明、カテゴリ名に一意な検査文字列を入れ、外部通信とログへ出ないことを確認する。
- 平文HTTP、無効証明書、ホスト名不一致、HTTPSからHTTPへのリダイレクトを拒否することを確認する。
- 不正Intent、欠落ID、巨大値、古い通知、再送されたPendingIntentを安全に拒否する。
- 改ざん、巨大、暗号化、ZIP Slip、Zip Bomb相当のバックアップを拒否する。
- 権限を拒否・取消してもクラッシュせず、基本機能を継続する。
- アンインストール後にアプリ専用データが残らず、外部バックアップは残ることを確認する。

## 4. 第三者SDK監査

公開候補ごとにAdMobとUMPを含む全SDKについて次を記録する。

- SDK名、バージョン、提供者、用途
- 追加される権限とコンポーネント
- 通信先、収集・共有するデータ、利用目的
- 同意前と拒否後の挙動
- 初期化時期とバックグラウンド処理
- 最新ポリシー、SDK Index上の警告、既知脆弱性
- プライバシーポリシーとData safetyの対応箇所

仕様と一致しないデータ収集、不要な権限、重大な未解決脆弱性があるSDKは公開版へ含めない。

## 5. 公開文書の照合

- プライバシーポリシーURLをアプリ内とGoogle Playの両方からHTTPSで開ける。
- ポリシーはログイン、地域制限、Cookie同意を要求せず閲覧できる。
- Data safetyの回答を全SDKの現行版と実通信に照合する。
- UMPの同意選択肢とプライバシーオプション入口を実際の広告設定へ照合する。
- 手動バックアップ、通知、ウィジェット、広告表示と同意の説明をポリシーと一致させる。
- 公開文書の改定日と対象アプリバージョンをリリース記録へ残す。

## 6. インシデント対応

- 漏えい、誤送信、秘密情報混入、署名鍵侵害、SDK脆弱性を検知した場合は公開を停止する。
- 影響するバージョン、データ、ユーザー、期間、通信先を特定する。
- 鍵やトークンは失効・再発行し、修正版では再利用しない。
- Google Play、SDK提供者、法令上必要な通知を確認する。
- 原因、封じ込め、修正、再発防止、公開判断を記録する。
- 影響が不明なままログや端末データを広範に収集しない。

## 7. 受け入れ条件

1. TODO、カテゴリ、履歴をアプリ専用領域だけへ保存し、外部通信へ含めない。
2. Android Auto Backupと端末間転送から全アプリデータを除外できる。
3. 宣言権限が承認済み一覧に限定され、拒否時も基本機能を利用できる。
4. 内部コンポーネントをexportせず、外部IntentとPendingIntentを再検証できる。
5. Releaseで平文通信を禁止し、無効なTLSと不正リダイレクトを拒否できる。
6. バックアップと外部URIをサイズ、形式、内容、参照関係まで検証できる。
7. 秘密情報と署名鍵がソース、APK、ログへ混入しない。
8. 依存関係を固定・検証し、重大な既知脆弱性を公開前に確認できる。
9. 通知の公開版にTODO内容を表示せず、ロック画面で内容を保護できる。
10. 全SDKのデータ処理をプライバシーポリシー、UMP、Data safetyへ一致させられる。
11. 外部通信検査でTODO内容とユーザー入力が送信されないことを確認できる。
12. セキュリティまたはプライバシー上の重大な未解決事項がある場合に公開を停止できる。
