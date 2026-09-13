# MATA 1.0.0 (5) 公開候補生成結果

- 試験ID: `REL-021`
- 実施日: 2026-09-13
- 実施者: `OWNER` / `AUTO`
- 判定: 合格
- 公開承認: Closed testingへのアップロード待ち
- Closed testing引き渡し: [登録・更新確認手順](closed-testing-release-1.0.0-5.md)

## 1. 候補の識別

| 項目 | 結果 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `5` |
| ソースcommit | `09cbbc85d9c8ecb9db3137cbd62e03c22b8dcb0d` |
| ソースブランチ | クリーンな`main` |
| ビルド日時 | `2026-09-13T06:50:04.715037300Z` |
| 署名方法 | Upload Key、単一署名者、`publishable=true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| ローカル保管先 | `app/release/1.0.0-5/`。Git除外対象 |

`app/release/1.0.0-5/`は次のビルドによる上書きを防ぐローカル複製であり、永続バックアップではない。AAB、mappingおよび検証メタデータをアクセス制限された保管先にも保存する。

## 2. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-5.aab` | 12,673,373 | `2ca02fc94c25ccbe29e33d4e38ae12388a52be72e8bc62c8e1d15ea12eb01533` |
| R8 mapping | `mapping.txt` | 86,696,854 | `1998b0936f34a447bb38b83aec981b89ca97ab0a66c05a03a5265dda14510ae6` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `e85023c38b0358b389fa72a2480c3f60e42e6666e23f7000b4fd414fe03ee07a` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `c931a2529f3d7842fb242740818b5c1f55403ea4f65c435d27d087e08c60df8e` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,847 | `c1ed8f6c8f5ed45453ea81862b3c8ac7a8893e3ea302b6e4f29f2298d9b4d9a1` |

同じディレクトリに`release-metadata.json`と`release-readiness.json`を保存した。両ファイルは上記成果物をソースcommit、versionCodeおよびビルド日時へ対応付け、署名秘密やkeystoreのパスを含まない。複製後に5成果物の容量とSHA-256がメタデータと一致することも再検証した。

## 3. 実行した検査

Upload Keyの秘密値をリポジトリ外のGradleユーザープロパティから読み込み、次を実行した。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
node tools/release/verify-readiness.mjs --release
```

公開候補検査はすべて合格した。

- ビルド構成: `1.0.0 (5)`、本番AdMob入力、法的URL、最適化およびBaseline Profile。
- Git: `main`、対象commit一致、追跡・未追跡の差分なし。
- 法的サイト: Releaseモードで4ページとsitemapの4 URLを検証。
- Play掲載成果物: versionCode 5のリリースノート、掲載文および全ストア画像を検証。
- 成果物: 5ファイルの存在、容量、SHA-256およびcommit一致。
- SBOM: Release runtime 226 componentと依存関係グラフを検証。
- 署名: Upload Key、署名者1件、期待する証明書SHA-256一致、`publishable=true`。
- CI: [PR #194のrun 34741198020](https://github.com/Sakemotti/MATA/actions/runs/34741198020)、[PR #195のrun 34741651049](https://github.com/Sakemotti/MATA/actions/runs/34741651049)および[PR #197のrun 34743486675](https://github.com/Sakemotti/MATA/actions/runs/34743486675)でRepository security、Debug、Release、Performance APKおよびAPI 30 instrumented testが成功。

Release AABにはネイティブライブラリを含む。`SYMBOL_TABLE`を有効化しているが、依存元ですでにシンボルが除去されているため、Play ConsoleでNative Debug Symbols未登録の既知警告が継続する可能性がある。空または架空のシンボルファイルは登録しない。

## 4. versionCode 5の変更範囲

- TODO一覧、カテゴリ別TODO、カレンダー履歴、カテゴリ管理、アーカイブ済TODO、設定およびTODO編集をカードベースのレイアウトへ統一した。
- 画面背景、カード色、角丸入力欄、画面外周余白およびカード間隔を共通化した。
- カレンダー履歴とアーカイブ済みTODOの読み取り専用詳細を全画面表示とし、情報を区分ごとに整理した。
- スマートフォン、7インチおよび10インチのストア画像を、上記カードレイアウトを反映した実画面へ更新した。
- versionCode 4以降に追加したカテゴリ別TODO、アーカイブおよびTODO編集等のDEV_AUTO試験と回帰検査を含む。

主要な画面変更は[PR #193](https://github.com/Sakemotti/MATA/pull/193)と[PR #194](https://github.com/Sakemotti/MATA/pull/194)、versionCode更新は[PR #195](https://github.com/Sakemotti/MATA/pull/195)、ストア画像更新は[PR #197](https://github.com/Sakemotti/MATA/pull/197)で統合した。

## 5. 判定範囲と残りゲート

本書の合格は、Upload Key署名済み成果物が機械検証済みのClosed testing候補として一意に識別できたことを示す。本番公開の承認ではない。

- versionCode `5`をClosed testingへアップロードし、Console上のversionName、versionCode、トラック、警告および公開状態を確認する。
- Google Play経由でversionCode `5`へ上書き更新し、既存データが保持されることを確認する。
- カードレイアウトと読み取り専用詳細を実機のライト・ダークテーマと主要表示サイズで確認する。
- 12人以上・14日間の継続参加、Production access申請および残りの公開ゲートを完了する。
