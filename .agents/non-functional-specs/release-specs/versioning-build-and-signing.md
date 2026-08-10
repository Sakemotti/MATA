# バージョン・ビルド・署名仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
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
  - 依存関係一覧とライセンス一覧
  - 最終Manifestと権限一覧
  - テスト・Lint・benchmark結果
  - ストア掲載文言とリリースノート
- ネイティブライブラリを導入した場合はNative Debug Symbolsもアップロード・保管する。
- CI成果物は原則1年、公開済みReleaseのメタデータとmappingは公開期間中および公開終了後3年以上保持する。

## 5. 署名

- Google Play App Signingを有効にする。
- App Signing KeyはGoogle Playに管理させる。
- Upload KeyはApp Signing Keyと分離し、アクセスを必要最小限にする。
- Upload Keyのkeystoreと復旧情報を暗号化した安全な保管先へ置き、Git、CI成果物、チャットへ添付しない。
- keystoreパスワードと鍵パスワードはパスワードマネージャーまたはCI Secretで管理する。
- CIでは保護されたRelease環境だけが署名Secretを参照できる。
- Upload Key紛失・侵害時はGoogle Playのリセット手順を使用し、App Signing Keyの変更を試みない。

## 6. 再現性と供給網

- Gradle Wrapper、JDK、AGP、Kotlin、依存関係バージョンを固定する。
- 動的依存を禁止し、Dependency VerificationとロックをCIで強制する。
- CIは毎回クリーン環境からビルドする。
- ビルドスクリプトからネットワーク上の任意スクリプトを直接実行しない。
- 生成されたライセンス一覧と依存関係一覧をRelease前に差分確認する。
- AABアップロード前にSHA-256を記録し、検証済みファイルと一致することを確認する。
