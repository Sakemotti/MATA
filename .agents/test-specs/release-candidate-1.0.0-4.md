# MATA 1.0.0 (4) 公開候補生成結果

- 試験ID: `REL-021`
- 実施日: 2026-09-11
- 実施者: `OWNER` / `AUTO`
- 判定: 合格
- 公開承認: Closed testingへのアップロード待ち
- Closed testing引き渡し: [登録・更新確認手順](closed-testing-release-1.0.0-4.md)

## 1. 候補の識別

| 項目 | 結果 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName | `1.0.0` |
| versionCode | `4` |
| ソースcommit | `fe71e1579969d647e95170e47f2d036b19e5b657` |
| ソースブランチ | クリーンな`main` |
| ビルド日時 | `2026-09-11T10:20:06.755458600Z` |
| 署名方法 | Upload Key、単一署名者、`publishable=true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| ローカル保管先 | `app/release/1.0.0-4/`。Git除外対象 |

`app/release/1.0.0-4/`は次のビルドによる上書きを防ぐローカル複製であり、永続バックアップではない。AAB、mappingおよび検証メタデータをアクセス制限された保管先にも保存する。

## 2. 成果物

| 種別 | ローカルファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-4.aab` | 12,637,169 | `9eb9fce3b57cdc2e1af4d676e1adada056fda81c5088725a956b597415106ef4` |
| R8 mapping | `mapping.txt` | 86,338,017 | `65efdcc8acf577cd515d900cc84d15a0b3028a82cd09d9c39080fced636d06d4` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `e85023c38b0358b389fa72a2480c3f60e42e6666e23f7000b4fd414fe03ee07a` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `be099636abc5a4eced7e9fe3b004a81bc9e9cb918daf4405d067ac58e60bde95` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,847 | `559617607ac271b354ee5b00a7559794130ccdab7dd710d47989c254569d10b7` |

同じディレクトリに`release-metadata.json`と`release-readiness.json`を保存した。両ファイルは上記成果物をソースcommit、versionCodeおよびビルド日時へ対応付け、署名秘密やkeystoreのパスを含まない。

## 3. 実行した検査

Upload Keyの秘密値をリポジトリ外のGradleユーザープロパティから読み込み、次を実行した。

```powershell
.\gradlew.bat :app:clean :app:generateReleaseArtifactMetadata -PMATA_REQUIRE_UPLOAD_SIGNING=true --no-configuration-cache --no-daemon
node tools/release/verify-readiness.mjs --release
```

公開候補検査はすべて合格した。

- ビルド構成: `1.0.0 (4)`、本番AdMob入力、法的URL、最適化およびBaseline Profile。
- Git: `main`、対象commit一致、追跡・未追跡の差分なし。
- 法的サイト: Releaseモードで4ページとsitemapの4 URLを検証。
- Play掲載成果物: versionCode 4のリリースノート、掲載文および全ストア画像を検証。
- 成果物: 5ファイルの存在、容量、SHA-256およびcommit一致。
- SBOM: Release runtime 226 componentと依存関係グラフを検証。
- 署名: Upload Key、署名者1件、期待する証明書SHA-256一致、`publishable=true`。
- CI: [PR #183のrun 34587951161](https://github.com/Sakemotti/MATA/actions/runs/34587951161)でRepository security、Debug、Release、Performance APKおよびAPI 30 instrumented testが成功。

Release AABにはネイティブライブラリを含む。`SYMBOL_TABLE`を有効化しているが、依存元ですでにシンボルが除去されているため、Play ConsoleでNative Debug Symbols未登録の既知警告が継続する可能性がある。空または架空のシンボルファイルは登録しない。

## 4. versionCode 4の変更範囲

- TODO一覧をデイリープランナー風のデザインへ更新し、日付操作、完了数、予定数および進捗をカードへ集約した。
- TODO一覧をカテゴリインデックスで区切り、行の視認性を改善した。
- カレンダー履歴をコンパクトなカレンダーカードへ更新した。
- 選択日の状態、完了数、予定数および進捗をサマリーカードへ集約し、履歴を状態インデックスで整理した。
- versionCode 3以降に追加したDEV_AUTO試験と回帰検査を含む。

主要な画面変更は[PR #181](https://github.com/Sakemotti/MATA/pull/181)と[PR #182](https://github.com/Sakemotti/MATA/pull/182)、versionCode更新は[PR #183](https://github.com/Sakemotti/MATA/pull/183)で統合した。

## 5. 判定範囲と残りゲート

本書の合格は、Upload Key署名済み成果物が機械検証済みのClosed testing候補として一意に識別できたことを示す。本番公開の承認ではない。

- versionCode `4`をClosed testingへアップロードし、Console上のversionName、versionCode、トラック、警告および公開状態を確認する。
- versionCode `3`から`4`へのGoogle Play経由の上書き更新と既存データ保持を確認する。
- デイリープランナーUIを実機のライト・ダークテーマと主要表示サイズで確認する。
- 12人以上・14日間の継続参加、Production access申請および残りの公開ゲートを完了する。
