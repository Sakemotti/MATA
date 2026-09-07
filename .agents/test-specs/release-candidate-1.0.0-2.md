# MATA 1.0.0 (2) 公開候補生成結果

- 試験ID: `REL-021`
- 実施日: 2026-09-07
- 実施者: `OWNER` / `AUTO`
- 判定: 合格
- 公開承認: 未承認
- Closed testing引き渡し: [登録・更新確認手順](closed-testing-release-1.0.0-2.md)

## 1. 候補の識別

| 項目 | 結果 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `2` |
| ソースcommit | `1222267981f2a7887e8c2073bbd7c2bd1a18a78e` |
| ソースブランチ | クリーンな`main` |
| ビルド日時 | `2026-09-06T23:34:53.482552400Z` |
| 署名方法 | Upload Key、単一署名者、`publishable=true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| ローカル保管先 | `app/release/1.0.0-2/`。Git除外対象 |

`app/release/1.0.0-2/`は次のビルドから成果物を保護するためのローカル複製であり、永続バックアップではない。AABをPlay Consoleへ登録する前に、成果物一式をアクセス制限されたリリース保管先へ複製する。

## 2. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-2.aab` | 12,538,422 | `a6f3a90728f14b1f45bb66dc8be141257aaac73c1d8a099caf7757d2e48137de` |
| R8 mapping | `mapping.txt` | 85,209,707 | `ea0c3fccfaee023ff972f6838c2176273e8b826eb14656601946b0ee24dd6b2a` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `2fb05d238fbc55a4960172ea5c858b50b0502b95e0f4480ff4e3a659bae8bf83` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `2961de446060b6bf4d81c67ede32344fafc0680a5ef32926406cee8ccf2edfc8` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,324 | `3e3c54ffec0820061672602e6ea6c394a21a6cda27f903fa28437d6cf981c1bc` |

同じディレクトリに`release-metadata.json`と`release-readiness.json`も保存した。これらには署名秘密やkeystoreのパスを含めず、上表の5成果物をcommit、versionCodeおよびビルド日時へ対応付ける。

## 3. 実行した検査

次の生成コマンドを、Upload Keyの秘密値をリポジトリ外のGradleユーザープロパティから読み込む状態で実行した。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
```

続けて、同じ作業ツリーと成果物に対して次を実行した。

```powershell
node tools/release/verify-readiness.mjs --release
```

結果はすべて合格した。

- ビルド構成: `1.0.0 (2)`、本番AdMob入力、法的URL、最適化およびBaseline Profile。
- Git: `main`、対象commit一致、追跡・未追跡の差分なし。
- 法的サイト原本: Releaseモードで4ページとsitemapの4 URLを検証。
- Play掲載成果物: versionCode 2のリリースノート、掲載文、アイコン、フィーチャーグラフィック、スマートフォン6枚、タブレット8枚を検証。
- 成果物: 5ファイルの存在、容量、SHA-256およびcommit一致。
- SBOM: Release runtime 226 componentと依存関係グラフを検証。
- 署名: Upload Key、署名者1件、期待する証明書SHA-256一致、`publishable=true`。
- Native Debug Symbols: [Android公式手順](https://developer.android.com/build/include-native-symbols?hl=ja)に従ってReleaseへ`SYMBOL_TABLE`を設定。AABには4 ABI合計8件のネイティブライブラリがあるが、依存元ですでにシンボルが除去されており、取得可能なシンボルは0件。空のシンボルファイルは作成していない。

[PR #145のCI](https://github.com/Sakemotti/MATA/actions/runs/34065561514)と[PR #146のCI](https://github.com/Sakemotti/MATA/actions/runs/34066935705)でも、Repository security、Debug、Release、Performance APKおよびAPI 30 instrumented testを含む全ジョブが成功した。

## 4. REL-021の判定範囲

`REL-021`の合格は、正式な法的文書・ストア素材とUpload Key署名済み成果物が揃い、自動検査済みの公開候補として一意に識別できたことを示す。本番公開を承認するものではない。

次は引き続き未完了とする。

- `1.0.0 (2)`のGoogle Play Closed testingへの登録と、Play Console処理後のAAB・versionCode確認。
- Closed testing参加、実機・テーマ・最大フォント・OS構成の試験結果。
- versionCode 1から2へのGoogle Play経由の上書き更新。
- Pre-launch report、SDK Index、権限、Data safetyおよびポリシー警告の候補版での確認。
- Native Debug Symbolsを取得できない依存ライブラリに関するPlay Console警告の再確認。これはクラッシュ解析品質の注意であり、公開可否はConsoleの現行判定に従う。
- 必要な法的専門家確認と最終人的承認。
- 全P0/P1を集計する`REL-010`。

アプリ実装、ビルド設定、法的本文またはPlay掲載成果物を変更した場合、この候補をそのまま再ビルド・再アップロードしない。versionCodeを3以上へ上げ、変更後のクリーンなmainから全ゲートを再実行する。
