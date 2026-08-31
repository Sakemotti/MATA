# バージョン・ビルド・署名仕様

- 文書状態: 確定
- 最終更新日: 2026-08-31
- 親仕様: [リリース・配布運用仕様](README.md)

## 1. バージョン

- `versionName`は`MAJOR.MINOR.PATCH`形式とする。初回公開は`1.0.0`とする。
- MAJORは互換性または利用方法を大きく変更する公開、MINORは後方互換な機能追加、PATCHは不具合・性能・セキュリティ修正で上げる。
- プレリリース表記は配布成果物のversionNameへ含めず、トラックとGitタグ候補で区別する。
- `versionCode`は正の整数で、Google Playへ一度でもアップロードした値より必ず大きくする。
- 最初の本番公開は`versionCode=1`とし、以後CIまたはリリース担当者が単調増加させる。
- 同じversionNameの再ビルドをPlayへ出す場合もversionCodeを上げる。
- バックアップ形式とDBスキーマはアプリバージョンから独立して管理する。

## 2. Git識別

- 公開候補はクリーンな`main`のコミットから作成する。
- 本番公開したコミットへ注釈付きタグ`v<versionName>`を付ける。
- タグは本番で実際に公開したコミットを指し、公開前の候補へ先行付与しない。
- Release成果物のメタデータへGit commit SHA、versionName、versionCode、ビルド日時を記録する。
- ソースへ未追跡・未コミット変更がある状態ではReleaseビルドを失敗させる。

## 3. Build type

### 3.1 Debug

- Application IDは`com.mochisofts.mata.debug`、表示名は`MATA Dev`とする。
- Debug署名、テスト広告、Fake外部サービス、詳細診断を使用できる。
- 本番商品を購入できず、本番広告を表示しない。

### 3.2 Release

- Application IDは`com.mochisofts.mata`、表示名は`MATA`とする。
- Debuggable無効、R8有効、resource shrink有効、Baseline Profile適用とする。
- 本番用AdMob ID、Billing商品、法的URLを必須値として検証する。
- テスト用Activity、Fake、障害注入、開発URL、テスト広告IDを成果物へ含めない。

## 4. ビルド成果物

- Google Play公開用は署名済みAABとする。
- APKはローカル・自動試験に限り、一般配布しない。
- 各Releaseで次を同じversionCodeへ対応付けて保管する。
  - AABのSHA-256
  - R8 mapping
  - Baseline Profileと生成元
  - Release runtimeのCycloneDX SBOMとライセンス一覧
  - 最終Manifestと権限一覧
  - テスト・Lint・benchmark結果
  - ストア掲載文言とリリースノート
- ネイティブライブラリを導入した場合はNative Debug Symbolsもアップロード・保管する。
- CI成果物は原則1年、公開済みReleaseのメタデータとmappingは公開期間中および公開終了後3年以上保持する。

### 4.1 Release SBOM

- CycloneDX Gradle Pluginの固定バージョンを使用し、CycloneDX 1.6 JSONを`app/build/outputs/sbom/release-sbom.cdx.json`へ生成する。
- 対象は`:app`の`releaseRuntimeClasspath`だけとし、Debug、テスト、benchmarkおよびビルドツールの依存関係を含めない。
- Releaseへ解決された直接・推移依存関係と依存関係グラフを記録する。
- ルートcomponentは`application`、groupは`com.mochisofts`、nameは`MATA`、versionはReleaseの`versionName`とする。
- ランダムなserial numberを含めず、SBOMのUTC timestampはReleaseメタデータの`buildTimestamp`と一致させる。
- ReleaseメタデータへSBOMの相対パス、バイト数およびSHA-256を記録し、AAB等と同じCI artifactへ保存する。
- 自動検査ではCycloneDX形式、ルートcomponent、依存関係グラフ、およびBilling、GMA Next-Gen SDK、UMPの存在を確認する。
- SBOMには依存ライブラリ情報だけを記録し、署名秘密、広告IDの値、購入トークン、ユーザーデータまたはローカルパスを含めない。

## 5. 署名

- Google Play App Signingを有効にする。
- App Signing KeyはGoogle Playに管理させる。
- Upload KeyはApp Signing Keyと分離し、アクセスを必要最小限にする。
- Upload Keyのkeystoreと復旧情報を暗号化した安全な保管先へ置き、Git、CI成果物、チャットへ添付しない。
- keystoreパスワードと鍵パスワードはパスワードマネージャーまたはCI Secretで管理する。
- CIでは保護されたRelease環境だけが署名Secretを参照できる。
- Upload Key紛失・侵害時はGoogle Playのリセット手順を使用し、App Signing Keyの変更を試みない。

### 5.1 Gradle入力

本番用AABの生成時は次の4項目をすべてGradleプロパティとして設定する。1項目でも不足した部分設定を許可しない。

| プロパティ | 内容 |
| --- | --- |
| `MATA_UPLOAD_STORE_FILE` | リポジトリ外に保管したkeystoreの絶対パス |
| `MATA_UPLOAD_STORE_PASSWORD` | keystoreのパスワード |
| `MATA_UPLOAD_KEY_ALIAS` | Upload Keyのalias |
| `MATA_UPLOAD_KEY_PASSWORD` | Upload Keyのパスワード |

- ローカルでは`GRADLE_USER_HOME/gradle.properties`へ保存し、プロジェクト内の`gradle.properties`、`keystore.properties`、シェル履歴またはIDEの共有Run Configurationへ記載しない。
- CIでは保護された環境のSecretから`ORG_GRADLE_PROJECT_`接頭辞付き環境変数として一時的に渡す。
- keystoreは絶対パスで指定し、リポジトリ配下のパスを拒否する。
- 秘密値4項目の名称や設定有無は検査できるが、値そのものをログ、JSONまたはActions artifactへ出力しない。

### 5.2 本番署名ビルド

本番公開候補では、非秘密値`MATA_REQUIRE_UPLOAD_SIGNING=true`も設定し、Configuration Cacheを無効化して次を実行する。

    ./gradlew :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon

- `MATA_REQUIRE_UPLOAD_SIGNING=true`で秘密値が不足する場合はビルドを失敗させる。
- 署名秘密が設定された状態でConfiguration Cacheが有効な場合は、秘密値をプロジェクトキャッシュへ保持しないためビルドを失敗させる。
- 生成したAABをJAR署名として検証し、署名者証明書のSHA-256をReleaseメタデータへ記録する。
- Releaseメタデータの`publishable`はUpload Key署名を検出した場合だけ`true`とする。署名済みであっても他の公開ゲートを省略しない。
- 通常CIは署名秘密と`MATA_REQUIRE_UPLOAD_SIGNING`を設定せず、未署名で`publishable=false`の検証用AABを生成する。

### 5.3 GitHub Actionsでの公開候補生成

`.github/workflows/release-candidate.yml`を、署名済み公開候補を生成する唯一のGitHub Actionsワークフローとする。

- `workflow_dispatch`から明示的に`confirm`を有効にした場合だけ起動し、自動のpush、Pull Requestまたはscheduleでは起動しない。
- `main`以外を選択した実行はBuild jobを開始せず、署名情報へアクセスしない。
- jobは`release-candidate` Environmentを参照し、公開操作を伴わないためGitHub Deploymentは作成しない。
- Environmentでは可能なプランでrequired reviewerと`main`だけのdeployment branch ruleを設定する。
- Environment Secretsを利用できない契約では同名のRepository Secretsを使用し、Repositoryへの管理権限を必要最小限にする。
- ワークフローは署名済みAABと証跡を生成するだけで、Google Playへのアップロード、トラック変更、公開、Gitタグ作成を行わない。
- artifactはPrivateリポジトリで設定済みの保持期間365日を使用する。公開済み版のAAB、mapping、メタデータおよび証明書SHA-256は期限前に別の安全な保管先へ移す。

`release-candidate` EnvironmentまたはRepositoryへ次を設定する。値をworkflowファイル、Actions Variables以外の平文ファイルまたはActionsログへ出力しない。

| 種別 | 名前 | 内容 |
| --- | --- | --- |
| Secret | `MATA_UPLOAD_KEYSTORE_BASE64` | Upload Key keystore全体をBase64化した値 |
| Secret | `MATA_UPLOAD_STORE_PASSWORD` | keystoreのパスワード |
| Secret | `MATA_UPLOAD_KEY_PASSWORD` | Upload Keyのパスワード |
| Variable | `MATA_UPLOAD_KEY_ALIAS` | Upload Keyのalias |
| Variable | `MATA_ADMOB_APP_ID` | 本番AdMob App ID |
| Variable | `MATA_ADMOB_BANNER_AD_UNIT_ID` | 本番バナー広告ユニットID |

ワークフローはSecretとVariableの存在を検証してから、keystoreをrunnerの一時領域へ復元する。Gradle実行ではConfiguration Cacheを無効化し、成否にかかわらず最後に一時keystoreを削除する。公開用検証、ストア画像検証および法的サイトの公開パッケージ生成がすべて成功した場合だけ、次を1つのActions artifactへ保存する。

- Upload Key署名済みAAB
- Releaseメタデータと公開用事前検査結果
- R8 mapping、CycloneDX SBOM、ライセンス一覧、最終Manifest
- Google Play掲載文と画像
- 法的文書サイトの公開パッケージと`SHA256SUMS`
- 上記全ファイルの相対パス、バイト数およびSHA-256を記録した`evidence-manifest.json`

ダウンロードした公開候補は、Google Playへのアップロード前に`verify-evidence.mjs verify`で検査する。GitHub上の実行commitと、安全な別経路で記録したUpload Key証明書SHA-256を期待値として指定し、artifact内の値だけを根拠に真正性を判断しない。

Actions実行サマリーへGitHubが計算したartifact ZIPのSHA-256を記録し、ダウンロードしたZIPの展開前検査に使用する。展開後はJDK 17の`jarsigner`と`keytool`により、AAB署名自体の有効性と実証明書SHA-256も検査する。

## 6. 再現性と供給網

- Gradle Wrapper、JDK、AGP、Kotlin、依存関係バージョンを固定する。
- 動的依存を禁止し、Dependency VerificationとロックをCIで強制する。
- CIは毎回クリーン環境からビルドする。
- ビルドスクリプトからネットワーク上の任意スクリプトを直接実行しない。
- 生成されたライセンス一覧とCycloneDX SBOMをRelease前に差分確認し、追加・更新・削除されたSDKをData safety、権限および外部送信公表と照合する。
- AABアップロード前にSHA-256を記録し、検証済みファイルと一致することを確認する。
