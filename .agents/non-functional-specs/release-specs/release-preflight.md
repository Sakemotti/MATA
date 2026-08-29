# Release事前検査仕様

- 文書状態: 確定
- 最終更新日: 2026-08-28
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
- 未確定の法的情報と未作成のストア画像は許容し、誤って公開可能とは判定しない。

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
- 法的文書の未確定値、検索除外、CNAME、app-ads.txtおよびストア画像の不足を許容しない。
- 成果物メタデータがUpload Key署名済みで、1件以上の署名者証明書SHA-256と`publishable=true`を持つ公開可能AABを示すことを要求する。
- 本モードの成功だけで公開承認とはせず、実機、Google Play Console、Pre-launch report、専門家確認および人的承認も完了させる。

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
