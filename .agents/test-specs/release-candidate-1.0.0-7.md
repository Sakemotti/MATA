# MATA 1.0.0 (7) Closed testing公開候補生成結果

- 状態: Upload Key署名済み候補生成・自動検証・Closed testing公開・上書き更新・テスター再確認済み
- 実施日: 2026-10-06
- 実施者: OWNER / AUTO
- 判定: 候補生成合格
- 登録手順: [MATA 1.0.0 (7) Closed testing登録・更新確認手順](closed-testing-release-1.0.0-7.md)

## 1. 候補の識別

| 項目 | 実績値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `7` |
| ソースcommit | `6cf969d8a13f66fc030267c784116e65c19b9da8` |
| ソースブランチ | クリーンな`main` |
| `origin/main`との一致 | 生成時に一致 |
| ビルド日時 | `2026-10-06T01:41:23.971064Z` |
| 署名方法 | Upload Key |
| 署名者数 | 1件 |
| `publishable` | `true` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |

## 2. 公開用AAB

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/1.0.0-7/mata-1.0.0-7.aab` |
| 容量 | `13,033,358 bytes` |
| SHA-256 | `F0EC8832D469891ABEF15EA59E6743915CAE3D011DD5DC36BEF009EFBA7259BB` |
| Google Play登録状態 | 2026-10-06にClosed testingで`公開完了`（USER確認） |

`app/release/`はGit除外対象である。
Play Consoleへアップロードするファイルは上表のAABだけとし、ZIPやmapping等をAAB欄へ登録しない。

## 3. 成果物

| 種別 | ファイル | bytes | SHA-256 |
| --- | --- | ---: | --- |
| Android App Bundle | `mata-1.0.0-7.aab` | 13,033,358 | `F0EC8832D469891ABEF15EA59E6743915CAE3D011DD5DC36BEF009EFBA7259BB` |
| R8 mapping | `mapping.txt` | 90,229,219 | `B82B2D355621AF640AD0ED39C23C6FC9C0DF55425D938C39053B4AAC07189CC0` |
| オープンソースライセンス | `aboutlibraries.json` | 108,031 | `3E0EDD2CC617315190A1C2E603D3A72570B82EF9F2727A5ECD13E4C17C12C70B` |
| 最終Manifest | `AndroidManifest.xml` | 13,461 | `B7F62435714B43C4434551B469260F6727B39C52FBDBE6BA3904963EBE6A42D1` |
| CycloneDX SBOM | `release-sbom.cdx.json` | 605,924 | `DC9FAB7F44840BE33C4ABA54B1460390B4AEFEE9D4557452B88B1DE86883BE7F` |

同じディレクトリへ`release-metadata.json`と`release-readiness.json`も保存した。
5成果物は生成元メタデータと、ローカル複製後に再計算した容量・SHA-256が一致した。

## 4. 保管用ZIP

| 項目 | 実績値 |
| --- | --- |
| ファイル | `app/release/MATA-1.0.0-7-release-candidate.zip` |
| 容量 | `19,263,378 bytes` |
| SHA-256 | `899610B8E3C3D38E7B25125CAF10290381DEB0B0630EE5C0C6C17BFB5229C334` |
| 収録ファイル数 | 7 |

ZIPは成果物一式の保管・受け渡し専用であり、Google Playへアップロードしない。

## 5. 自動検証

- [x] PR #278の[Android CI run 37316570956](https://github.com/Sakemotti/MATA/actions/runs/37316570956)でRepository security、Debug、Release、Performance APK、API 30 instrumented testおよび最終集約が合格した。
- [x] mainの[Android CI run 37318481519](https://github.com/Sakemotti/MATA/actions/runs/37318481519)が合格した。
- [x] `node tools/release/verify-readiness.mjs --release`の全7検査が合格した。
- [x] versionName `1.0.0`、versionCode `7`、mainのcommit、クリーンな作業ツリーおよびストア情報が一致した。
- [x] 5成果物のハッシュ、226 runtime component、単一Upload Key署名者および期待する証明書SHA-256が一致した。
- [x] P0/P1は405/405件合格である。

## 6. 既知警告と保留

- `libandroidx.graphics.path.so`と`libdatastore_shared_counter.so`は依存元ですでにシンボルが除去されており、ビルド時にstrip不可の既知警告が表示された。
- Play Consoleでネイティブデバッグシンボル未登録警告が表示される可能性がある。空または架空のシンボルファイルは登録せず、実際の警告を記録する。
- Google Playへの登録は2026-10-06に完了した。2026-10-07にversionCode `5`から`7`への上書き更新とデータ保持を確認し、Closed testing参加者による背景色設定の再確認にも合格した。更新端末・OSおよび再確認者の匿名IDは未記録である。
- Console警告は`ネイティブコードを含むがデバッグシンボル未登録`だけで、新しいエラー、SDK警告またはポリシー警告は報告されていない。

## 7. 判定

versionCode `7`のClosed testing公開候補生成は合格とする。
Productionへは自動昇格せず、Production access再申請前のConsole確認と承認後の最終公開判定を別途実施する。
