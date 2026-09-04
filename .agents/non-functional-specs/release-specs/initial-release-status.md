# 初回リリース進行記録

- 対象: MATA `1.0.0 (1)`
- 状態: Internal testing確認済み・Closed testing準備中
- 最終更新日: 2026-09-04
- 親仕様: [リリース・配布運用仕様](README.md)
- 公開判定基準: [リリースチェックリスト](release-checklist.md)
- 試験状況: [初回リリース試験棚卸し](../../test-specs/initial-release-inventory.md)
- Closed testing記録: [初回Closed testing実施台帳](../../test-specs/closed-testing-log.md)

## 1. 記録方針

本書は初回リリース固有の進行状況を記録する。仕様書とリリースチェックリストは再利用する基準文書であり、その未選択チェックボックスを本書の完了状況で上書きしない。

確認元を次のように区別する。

- `AUTO`: リポジトリまたはCIによる機械検査
- `CONSOLE`: Google Play ConsoleまたはAdMobの表示
- `DEVICE`: Google Playから取得したReleaseの実機確認
- `WEB`: 認証なしの公開URL確認
- `USER`: ユーザーが外部サービス上の操作結果を確認

## 2. リリース識別と成果物

| 項目 | 確定値・結果 | 確認元 |
| --- | --- | --- |
| Application ID | `com.mochisofts.mata` | AUTO |
| versionName | `1.0.0` | AUTO / CONSOLE |
| versionCode | `1`。Google Playへ登録済みで再アップロード不可 | AUTO / CONSOLE |
| ソースcommit | `e57ababd3b6fb4ad12bf57dada776e9189288dbc` | AUTO |
| AAB | `app/build/outputs/bundle/release/app-release.aab` | AUTO |
| AAB容量 | 12,531,673 bytes | AUTO |
| AAB SHA-256 | `ccf43a79a7d2e881f69e58fe85a98a68d1cf86249a9c4a9f1865a69f1275852c` | AUTO |
| ビルド日時 | `2026-09-03T12:03:48.374147600Z` | AUTO |
| 署名方法 | Upload Key、署名者1件、`publishable=true` | AUTO |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` | AUTO / CONSOLE |

Closed testingではこのAABを再アップロードせず、Internal testingから昇格するか、Google Playの成果物ライブラリから追加する。新しいAABをアップロードする場合はversionCodeを`2`以上にする。

## 3. リポジトリと自動検査

2026年9月4日の同期開始時点で次を確認した。

- `main`は`origin/main`と一致し、作業ツリーはクリーンだった。
- 未解決のGitHub IssueおよびPull Requestは0件だった。
- 対象commitを含む[Android CI](https://github.com/Sakemotti/MATA/actions/runs/33751810640)は成功した。
- PR #124のCIではAPI 30エミュレータ上のinstrumented test 47件が成功し、単体テスト、Lint、Debug・Release・Benchmark成果物生成とリリース成果物検査も成功した。
- Release成果物メタデータはAAB、R8 mapping、ライセンス、最終ManifestおよびCycloneDX SBOMの存在、容量、SHA-256を記録し、Upload Key証明書と一致した。
- リリース進行状況だけを記録する文書変更は、既にGoogle Playへ登録したAABを無効化しない。アプリ実装、ビルド設定、掲載成果物または法的本文を変更した場合は、versionCodeを上げた最終公開候補を新しいcommitから生成して全ゲートを再実行する。

## 4. Google Play・Web・実機で確認済み

| 項目 | 状態 | 確認元 |
| --- | --- | --- |
| 個人デベロッパー本人確認 | 完了 | CONSOLE / USER |
| 連絡先メール・電話番号 | 確認完了 | CONSOLE / USER |
| Android実機確認 | 完了 | CONSOLE / USER |
| Play ConsoleのMATAアプリ作成 | 完了 | CONSOLE / USER |
| App content | 未完了カードなし | CONSOLE / USER |
| ストア掲載文・画像・連絡先・Webサイト | 登録済み | CONSOLE / USER |
| Internal testing | `1.0.0 (1)`を公開し、参加URL経由でインストール済み | CONSOLE / DEVICE |
| Play App Signing | 有効。Upload Key証明書が本書の値と一致 | CONSOLE |
| Upload Keyバックアップ | keystoreと復旧情報を暗号化された安全な別保管先へ保存済み | USER |
| 新規インストールと起動 | 問題なし | DEVICE |
| TODO等の主要機能 | 現状問題なし | DEVICE / USER |
| 通知・ウィジェット | 現状問題なし | DEVICE / USER |
| 手動バックアップ・復元 | 現状問題なし | DEVICE / USER |
| アプリ設定から法的ページへの遷移 | 確認済み | DEVICE / USER |

2026年9月4日に、次のURLが認証なしでHTTP 200を返すことを確認した。

- `https://mochisofts.com/`
- `https://mochisofts.com/mata/privacy`
- `https://mochisofts.com/mata/terms`
- `https://mochisofts.com/mata/external-transmission`
- `https://mochisofts.com/app-ads.txt`

`app-ads.txt`は`text/plain`で配信され、Publisher ID `pub-6387608801909086`を含む正式な1行と一致した。

## 5. Closed testingと本番アクセス

- 状態はClosed testing準備中とする。
- Internal testingのversionCode `1`を昇格または成果物ライブラリから追加する。
- 対象アカウントの要件に従い、12人以上のテスターが14日間連続してオプトインした状態を維持する。
- テスターには実際にアプリを利用してもらい、主要機能、通知、ウィジェット、バックアップおよび端末情報を伴うフィードバックを収集する。
- 条件達成後、テスト方法、参加状況、フィードバック、修正内容を整理してProduction accessを申請する。

## 6. 未完了・保留

### 6.1 Closed testing中に完了する

- Closed testingの公開、テスターのオプトインおよび14日間の継続参加
- テスターの端末・OS・操作範囲・結果・フィードバックの記録
- API 26、33、36、タブレット、分割画面、最大フォント、ダークテーマおよびTalkBackの不足分確認
- Google PlayのSDK Index、権限申告およびポリシー警告の最終確認
- 利用規約と外部送信に関する公表について必要な専門家確認
- Data safety、UMP、SDK、実通信および公開法的文書の最終突合

### 6.2 次の実変更で確認する

- versionCode `2`以上を同じテスト対象へ配信し、Google Play経由の上書き更新を確認する。
- 更新後もTODO、カテゴリ、履歴、設定、通知およびウィジェットが維持されることを確認する。
- 修正を含む場合は全自動ゲート、署名、成果物ハッシュおよび主要な回帰試験を再実行する。

### 6.3 外部状態待ち

- Pre-launch reportはまだ生成されていない。Closed testing公開後と次回AAB登録時に再確認する。
- Internal testing限定のMATAはAdMobのGoogle Play検索とURL検索で見つからず、ストア連携できていない。
- AdMob側の`app-ads.txt`検証、アプリ準備状況審査および実広告表示は、Google Playからアプリをリンク可能になった後に確認する。
- 本番広告を試験目的でクリックしない。

## 7. 本番公開前後の残りゲート

1. Closed testing要件を達成し、Production accessの承認を得る。
2. 公開対象commit、versionName、versionCode、リリースノートおよびAABを確定する。
3. `1.0.0 (1)`を変更せず昇格する場合は、本書のソースcommit、AAB SHA-256および保存済み成果物を再照合する。アプリまたは公開成果物を変更した場合は、versionCodeを上げ、クリーンな`main`から署名済み成果物を生成して`node tools/release/verify-readiness.mjs --release`を成功させる。
4. 全P0/P1試験、Pre-launch report、権限、Data safety、SDK Indexおよび法的確認を完了する。
5. 初期配布地域を日本としてProductionへ公開する。
6. 公開後にGoogle Playからの新規インストール、AdMob連携・広告表示、Android vitalsおよびポリシー状態を確認する。
7. 公開日、最終AAB SHA-256、リリースノート、正本・公開サイトのcommitおよびGitタグを記録する。
