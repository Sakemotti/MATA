# 初回リリース進行記録

- 対象: MATA `1.0.0 (2)`
- 状態: versionCode 2をClosed testingへ公開済み・テスト実施中
- 最終更新日: 2026-09-07
- 親仕様: [リリース・配布運用仕様](README.md)
- 公開判定基準: [リリースチェックリスト](release-checklist.md)
- 試験状況: [初回リリース試験棚卸し](../../test-specs/initial-release-inventory.md)
- Closed testing記録: [初回Closed testing実施台帳](../../test-specs/closed-testing-log.md)
- Closed testing引き渡し: [MATA 1.0.0 (2) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-2.md)
- 公開候補証跡: [MATA 1.0.0 (2) 公開候補生成結果](../../test-specs/release-candidate-1.0.0-2.md)

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
| versionCode | `2`。Google Play Closed testingへ登録・公開済み | AUTO / CONSOLE / USER |
| ソースcommit | `1222267981f2a7887e8c2073bbd7c2bd1a18a78e` | AUTO |
| AAB | `app/release/1.0.0-2/mata-1.0.0-2.aab`。Git除外対象 | AUTO |
| AAB容量 | 12,538,422 bytes | AUTO |
| AAB SHA-256 | `a6f3a90728f14b1f45bb66dc8be141257aaac73c1d8a099caf7757d2e48137de` | AUTO |
| ビルド日時 | `2026-09-06T23:34:53.482552400Z` | AUTO |
| 署名方法 | Upload Key、署名者1件、`publishable=true` | AUTO |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` | AUTO / CONSOLE |

versionCode `1`はcommit`e57ababd3b6fb4ad12bf57dada776e9189288dbc`から生成してInternal testingへ登録済みであり、再アップロードできない。登録済みAABは12,531,673 bytes、SHA-256は`ccf43a79a7d2e881f69e58fe85a98a68d1cf86249a9c4a9f1865a69f1275852c`である。その後にアプリ実装が変更されたため、Closed testingと初回本番公開には上表のversionCode `2`を使用する。

## 3. リポジトリと自動検査

2026年9月7日にversionCode `2`について次を確認した。

- `main`のcommit`1222267981f2a7887e8c2073bbd7c2bd1a18a78e`から、クリーンな作業ツリーで署名済み成果物を生成した。
- `node tools/release/verify-readiness.mjs --release`は、Git、法的文書、Play掲載成果物、5成果物のハッシュ、226 runtime componentおよびUpload Key署名を含む全検査に成功した。
- [PR #145のCI](https://github.com/Sakemotti/MATA/actions/runs/34065561514)と[PR #146のCI](https://github.com/Sakemotti/MATA/actions/runs/34066935705)は、Repository security、Debug、Release、Performance APKおよびAPI 30 instrumented testを含む全ジョブに成功した。
- Release AABには4 ABI合計8件のネイティブライブラリがある。`SYMBOL_TABLE`を有効化したが、依存元ですでにシンボルが除去されているため取得可能なNative Debug Symbolsは0件だった。架空または空のシンボルファイルは作成せず、Play Consoleの警告を候補登録後に再確認する。
- 未解決のGitHub IssueおよびPull Requestは0件だった。
- PR #126のCIではAPI 30エミュレータ上のinstrumented test 54件が成功し、単体テスト、Lint、Debug・Release・Benchmark成果物生成とリリース成果物検査も成功した。
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
| `1.0.0 (2)`公開候補 | 署名済みAAB生成とRelease事前検査に成功。Closed testingへ公開済み | AUTO / CONSOLE / USER |
| Play App Signing | 有効。Upload Key証明書が本書の値と一致 | CONSOLE |
| Upload Keyバックアップ | keystoreと復旧情報を暗号化された安全な別保管先へ保存済み | USER |
| 新規インストールと起動 | 問題なし | DEVICE |
| TODO等の主要機能 | 現状問題なし | DEVICE / USER |
| 通知・ウィジェット | 現状問題なし | DEVICE / USER |
| 手動バックアップ・復元 | 現状問題なし | DEVICE / USER |
| アプリ設定から法的ページへの遷移 | 確認済み | DEVICE / USER |
| AdMobとGoogle Playのアプリ連携 | 確認済み | CONSOLE / USER |
| 実広告バナー表示 | 確認済み。本番広告のクリックは実施していない | DEVICE / USER |
| Closed testing参加要件 | 12人以上が14日間連続してオプトイン | CONSOLE / USER |
| ポリシーのステータス | `問題は見つかりませんでした` | CONSOLE / USER |
| versionCode `2`のSDK関連警告 | なし | CONSOLE / USER |

2026年9月4日に、次のURLが認証なしでHTTP 200を返すことを確認した。

- `https://mochisofts.com/`
- `https://mochisofts.com/mata/privacy`
- `https://mochisofts.com/mata/terms`
- `https://mochisofts.com/mata/external-transmission`
- `https://mochisofts.com/app-ads.txt`

`app-ads.txt`は`text/plain`で配信され、Publisher ID `pub-6387608801909086`を含む正式な1行と一致した。

### 4.1 法的サイト同期

2026年9月7日に、正本側commit`45ead6a73ac742899cb812e93fbbb0c01f1cf68f`と公開側commit`ad26db4aed4f97eaed7d74d66bcc44d63d90fda2`を対応付けた。公開側の[Pages deployment run 33757973477](https://github.com/Sakemotti/matadoc/actions/runs/33757973477)は成功している。

正本13ファイルと公開リポジトリmainの対応する13ファイルはGit blob SHAがすべて一致した。2026年9月7日06:50 JSTにモバイルUser-Agentでプライバシーポリシー、利用規約、外部送信公表および`app-ads.txt`を再取得し、全件HTTP 200かつ正本と同一であることを確認した。アプリ設定画面からのプライバシーポリシー・利用規約遷移は実機確認済みである。詳細は[法的サイト同期結果](../../test-specs/legal-site-sync-results.md)に記録する。

## 5. Closed testingと本番アクセス

- 2026年9月7日にversionName `1.0.0`、versionCode `2`のAABをClosed testingへ公開した。
- Console上のエラーは0件で、Native Debug Symbols未登録の警告だけが表示された。依存ライブラリで取得可能なシンボルがないことを確認済みのため、警告を記録して公開を継続した。
- オプトインURLを取得できることを確認した。URLおよびテスター情報はリポジトリへ記録せず、制限された連絡経路で管理する。
- 2026年9月8日にPixel 9a（Android 17 / API 37）で、既存versionCode `1`からGoogle Play経由でversionCode `2`へアンインストールせず更新できることを確認した。TODO、カテゴリ、履歴、設定、通知およびウィジェットは維持され、基準バックアップ`B0`を作成した。
- 対象アカウントの要件に従い、12人以上のテスターが14日間連続してオプトインした状態を維持する。
- テスターには試験項目を割り当てず、実際に複数日にわたって自由操作してもらい、利用状況と自由記述のフィードバックを収集する。
- 条件達成後、テスト方法、参加状況、フィードバック、修正内容を整理してProduction accessを申請する。

## 6. 未完了・保留

### 6.1 Closed testing中に完了する

- テスターのオプトインおよび14日間の継続参加
- テスターの端末・OS・利用期間・自由操作のフィードバックの記録
- API 26、33、36、タブレット、分割画面、最大フォント、ダークテーマおよびTalkBackの不足分確認
- Google PlayのSDK Index、権限申告およびポリシー警告の最終確認
- 利用規約と外部送信に関する公表について必要な専門家確認
- Data safety、UMP、SDK、実通信および公開法的文書の最終突合

### 6.2 次の実変更で確認する

- 修正を含む場合は全自動ゲート、署名、成果物ハッシュおよび主要な回帰試験を再実行する。

### 6.3 外部状態待ち

- 2026年9月7日時点のPre-launch reportは`リリース前レポートを生成するにはアーティファクトをアップロードしてください`と表示され、Closed testingへ登録済みのversionCode `2`に対するレポートは未生成である。レポート生成だけを目的とする追加AABは登録せず、Console側の反映を待って再確認する。
- AdMobとGoogle Playのアプリ連携および実広告バナー表示は2026年9月7日に確認済みである。AdMob側の`app-ads.txt`検証状態、アプリ準備状況およびポリシー警告は最終公開判定時に再確認する。
- 本番広告を試験目的でクリックしない。

## 7. 本番公開前後の残りゲート

1. Closed testing要件を達成し、Production accessの承認を得る。
2. 公開対象commit、versionName、versionCode、リリースノートおよびAABを確定する。
3. 上表の`1.0.0 (2)`、ソースcommit、AAB SHA-256および保存済み成果物を再照合する。アプリまたは公開成果物を変更した場合はversionCodeを`3`以上へ上げ、クリーンな`main`から署名済み成果物を再生成して`node tools/release/verify-readiness.mjs --release`を成功させる。
4. 全P0/P1試験、Pre-launch report、権限、Data safety、SDK Indexおよび法的確認を完了する。
5. 初期配布地域を日本としてProductionへ公開する。
6. 公開後にGoogle Playからの新規インストール、Android vitalsおよびポリシー状態を確認する。
7. 公開日、最終AAB SHA-256、リリースノート、正本・公開サイトのcommitおよびGitタグを記録する。
