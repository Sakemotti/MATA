# Release事前検査仕様

- 文書状態: 確定
- 最終更新日: 2026-09-04
- 親仕様: [リリース・配布運用仕様](README.md)
- 関連文書: [リリースチェックリスト](release-checklist.md)、[バージョン・ビルド・署名仕様](versioning-build-and-signing.md)

## 1. 目的

Release候補の設定、法的文書、Google Play掲載成果物およびビルド成果物を一つのコマンドで検査し、確認結果を機械可読な証跡として残す。検査は外部サービスへデータを送信せず、Google Playへのアップロードや公開を行わない。

## 2. 検査モード

### 2.1 公開前原稿

    node tools/release/verify-readiness.mjs

- Application ID、SDK、バージョン形式、最適化、Baseline Profile、広告・法的URL入力を静的検査する。
- Gitコミットとブランチを記録するが、開発中のブランチや差分を許容する。
- 法的文書サイトとPlay掲載文を公開前モードで検査する。
- 正式な`app-ads.txt`と未作成のストア画像は許容するが、公開可能とは判定しない。

### 2.2 CI成果物

    node tools/release/verify-readiness.mjs --artifacts

- 公開前原稿モードの全項目を検査する。
- `generateReleaseArtifactMetadata`が生成したAAB、R8 mapping、ライセンス一覧、CycloneDX SBOMおよび最終Manifestの存在、バイト数、SHA-256を再計算する。
- SBOMがCycloneDX 1.6で、Release runtimeのcomponentと依存関係グラフを持ち、アプリ識別子、ビルド日時および必須SDKがReleaseメタデータと一致することを確認する。
- メタデータのGit commitが検査時のHEADと一致することを確認する。
- Upload Key未署名のCI検証用AABでは、署名方法が`none`、証明書SHA-256が空、`publishable=false`であることを確認する。本番公開可能とは判定しない。

### 2.3 本番公開候補

    node tools/release/verify-readiness.mjs --release

- `main`ブランチのクリーンな作業ツリーだけを許可する。
- 法的文書のプレースホルダー、検索除外、CNAMEの不一致、正式な`app-ads.txt`およびストア画像の不足を許容しない。
- 成果物メタデータがUpload Key署名済みで、期待値と一致する単一の署名者証明書SHA-256と`publishable=true`を持つ公開可能AABを示すことを要求する。
- 本モードの成功だけで公開承認とはせず、実機、Google Play Console、Pre-launch report、専門家確認および人的承認も完了させる。
- 本モードは別のGitHub Pages公開リポジトリへのコピーやデプロイ結果を変更しない。成功後も正本側commitと公開側commitの対応、公開URLおよびアプリからの遷移を手動確認する。

## 3. 証跡

結果は`app/build/outputs/release-metadata/release-readiness.json`へ出力する。次を記録する。

- スキーマバージョン、検査モード、UTC検査日時、総合結果
- 各検査ID、合否および判断理由
- Gitブランチとcommit
- Release成果物のバージョンとハッシュ照合結果

CI成果物モードのJSONは、AAB、Releaseメタデータ、R8 mapping、ライセンス、CycloneDX SBOMおよび最終Manifestと同じActions artifactへ保存する。

## 4. 失敗時の扱い

- 失敗を無視して公開用AABをアップロードしない。
- 原稿・画像の不足は該当する正本仕様を更新してから再検査する。
- Git commit不一致またはハッシュ不一致では成果物を破棄し、クリーンな同一commitから再生成する。
- Application ID、署名、権限、SDKまたは収集データの差異は公開を停止し、仕様・申告・実装を同時に見直す。
- 本番公開候補モードの失敗項目は公開ブロッカーとしてリリース記録へ残す。

## 5. 検査ロジックの回帰試験

次のコマンドでRelease成果物の整合性検査自体を回帰試験する。この試験は一時ディレクトリ内の架空の成果物だけを使用し、実際のAABや署名情報を変更しない。

    node --test tools/release/release-artifact-verifier.test.mjs

正常な成果物一式を受理し、SBOMの内容、パス、ファイル有無、容量、SHA-256、必須componentおよび依存関係グラフの欠損・改変を、それぞれ対象名を含む理由で拒否できることを確認する。Pull Requestと`main`のCIではRepository security checksの一部として毎回実行する。

Upload Key設定の安全側失敗は次のコマンドで確認する。

    ./gradlew :app:verifyUploadSigningGuards --no-configuration-cache --no-daemon

秘密値の一部設定、相対パス、リポジトリ内ファイル、署名必須フラグなし、および署名時のConfiguration Cache有効化をすべて拒否し、例外へ架空の秘密値を含めないことを検証する。Release verificationのCIでも同じタスクを実行する。

ローカルのGradleユーザープロパティへUpload Keyの4秘密値を保存している環境では、通常の署名ビルドと同様に`-PMATA_REQUIRE_UPLOAD_SIGNING=true`を付ける。これは実際のローカル設定に対する安全確認であり、タスク内の異常系には架空の値だけを使用する。
