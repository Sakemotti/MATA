# MATA 1.0.0 (3) 公開候補生成結果

- 試験ID: `REL-021`
- 実施日: 2026-09-09
- 実施者: `OWNER` / `AUTO`
- 判定: 合格
- 公開承認: Closed testingへの公開をUSER確認済み
- Closed testing引き渡し: [登録・更新確認手順](closed-testing-release-1.0.0-3.md)

## 1. 候補の識別

| 項目 | 結果 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `3` |
| ソースcommit | `307949e2068b1c56ff597c3730b05755b1f37e06` |
| ソースブランチ | クリーンな`main` |
| ビルド日時 | `2026-09-09T01:47:56.549427400Z` |
| 署名方法 | Upload Key、単一署名者、`publishable=true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| ローカル保管先 | `app/release/1.0.0-3/`。Git除外対象 |

`app/release/1.0.0-3/`は次のビルドによる上書きを防ぐローカル複製であり、永続バックアップではない。AAB、mappingおよび検証メタデータをアクセス制限された保管先にも保存する。

## 2. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-3.aab` | 12,600,782 | `75a79da8534841cda3312594a086369d1e7ff53029920b56904f856b16fbe019` |
| R8 mapping | `mapping.txt` | 85,844,150 | `44d28f2f55d253f4dc381d69f89743d21a611adcf1123daee7e212a1f9fed161` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `e85023c38b0358b389fa72a2480c3f60e42e6666e23f7000b4fd414fe03ee07a` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `783a084e005d249a2ead7cbaf1faec9699fc3ac4928afdcc118d1d5f8606cacc` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,847 | `c1519be0f6b8908808254caefeab1890ee9f16ac38734fc9c2434d716ea7dda2` |

同じディレクトリに`release-metadata.json`と`release-readiness.json`を保存した。両ファイルは上記成果物をソースcommit、versionCodeおよびビルド日時へ対応付け、署名秘密やkeystoreのパスを含まない。

## 3. 実行した検査

Upload Keyの秘密値をリポジトリ外のGradleユーザープロパティから読み込み、次を実行した。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
node tools/release/verify-readiness.mjs --release
```

公開候補検査はすべて合格した。

- ビルド構成: `1.0.0 (3)`、本番AdMob入力、法的URL、最適化およびBaseline Profile。
- Git: `main`、対象commit一致、追跡・未追跡の差分なし。
- 法的サイト: Releaseモードで4ページとsitemapの4 URLを検証。
- Play掲載成果物: versionCode 3のリリースノート、掲載文および全ストア画像を検証。
- 成果物: 5ファイルの存在、容量、SHA-256およびcommit一致。
- SBOM: Release runtime 226 componentと依存関係グラフを検証。
- 署名: Upload Key、署名者1件、期待する証明書SHA-256一致、`publishable=true`。
- CI: [main候補と同一ソースを検証したrun 34299331024](https://github.com/Sakemotti/MATA/actions/runs/34299331024)でRepository security、Debug、Release、Performance APKおよびAPI 30 instrumented testが成功。

Release AABには4 ABI合計8件のネイティブライブラリがある。`SYMBOL_TABLE`を有効化しているが、依存元ですでにシンボルが除去されており、AAB内のNative Debug Symbolsは0件だった。空または架空のシンボルファイルは登録しない。

## 4. versionCode 3の変更範囲

- 単発TODOで実行日とは別の期限日を設定できる。
- 未完了TODOを完了またはスキップするまで翌日以降も表示する繰り越し設定を追加した。
- 過去日のTODO詳細から現在のTODOを編集・完全削除できる。
- 入力欄でカテゴリ色を確認しやすくした。

変更の主要実装は[PR #168](https://github.com/Sakemotti/MATA/pull/168)で統合した。候補commitには、その後mainへ統合した依存関係更新とIDE・Gradle共有設定の整理も含む。

## 5. 判定範囲と残りゲート

本書の合格は、Upload Key署名済み成果物が機械検証済みのClosed testing候補として一意に識別できたことを示す。本番公開の承認ではない。

- 2026年9月9日にversionCode `3`をClosed testingへ公開したことをUSERが確認した。
- Play Console上の正確な公開状態文言、処理日時、警告およびPre-launch reportはConsole確認後に台帳へ追記する。
- versionCode `2`から`3`へのGoogle Play経由の上書き更新と、データ・設定・通知・ウィジェット保持を確認する。
- versionCode 3で修正したClosed testingフィードバックを再試験する。
- 12人以上・14日間の継続参加、Production access申請および残りの公開ゲートを完了する。
