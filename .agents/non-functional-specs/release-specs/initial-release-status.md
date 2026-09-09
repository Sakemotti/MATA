# 初回リリース進行記録

- 対象: MATA `1.0.0 (3)`
- 状態: versionCode 3をClosed testingへ公開・上書き更新済み／変更内容と不足環境の再試験中
- 最終更新日: 2026-09-09
- 親仕様: [リリース・配布運用仕様](README.md)
- 公開判定基準: [リリースチェックリスト](release-checklist.md)
- 試験状況: [初回リリース試験棚卸し](../../test-specs/initial-release-inventory.md)
- Closed testing記録: [初回Closed testing実施台帳](../../test-specs/closed-testing-log.md)
- Closed testing引き渡し: [MATA 1.0.0 (3) 登録・更新確認手順](../../test-specs/closed-testing-release-1.0.0-3.md)
- 公開候補証跡: [MATA 1.0.0 (3) 公開候補生成結果](../../test-specs/release-candidate-1.0.0-3.md)

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
| versionCode | `3`。Google Play Closed testingへ登録・公開済み | AUTO / CONSOLE / USER |
| ソースcommit | `307949e2068b1c56ff597c3730b05755b1f37e06` | AUTO |
| AAB | `app/release/1.0.0-3/mata-1.0.0-3.aab`。Git除外対象 | AUTO |
| AAB容量 | 12,600,782 bytes | AUTO |
| AAB SHA-256 | `75a79da8534841cda3312594a086369d1e7ff53029920b56904f856b16fbe019` | AUTO |
| ビルド日時 | `2026-09-09T01:47:56.549427400Z` | AUTO |
| 署名方法 | Upload Key、署名者1件、`publishable=true` | AUTO |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` | AUTO / CONSOLE |

versionCode `1`はcommit`e57ababd3b6fb4ad12bf57dada776e9189288dbc`から生成してInternal testingへ登録済みであり、再アップロードできない。versionCode `2`はcommit`1222267981f2a7887e8c2073bbd7c2bd1a18a78e`から生成してClosed testingへ公開し、Pixel 9aでversionCode `1`からの上書き更新を確認した。その後のClosed testingフィードバックを反映した現在候補が上表のversionCode `3`である。

## 3. リポジトリと自動検査

2026年9月9日にversionCode `3`について次を確認した。

- `main`のcommit`307949e2068b1c56ff597c3730b05755b1f37e06`から、クリーンな作業ツリーで署名済み成果物を生成した。
- `node tools/release/verify-readiness.mjs --release`は、Git、法的文書、Play掲載成果物、5成果物のハッシュ、226 runtime componentおよびUpload Key署名を含む全検査に成功した。
- [CI run 34299331024](https://github.com/Sakemotti/MATA/actions/runs/34299331024)は、Repository security、Debug、Release、Performance APKおよびAPI 30 instrumented testを含む全ジョブに成功した。
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
| `1.0.0 (2)`公開候補 | Closed testingへ公開し、versionCode 1からの上書き更新と主要実機試験に使用済み | AUTO / CONSOLE / DEVICE / USER |
| `1.0.0 (3)`公開候補 | 署名済みAAB生成とRelease事前検査に成功。Closed testingへ公開済み | AUTO / CONSOLE / USER |
| versionCode `2`→`3`上書き更新 | Pixel 9a（Android 17 / API 37）で成功。既存データ、設定、通知、ウィジェットおよびバックアップに問題なし | DEVICE / USER |
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
- 2026年9月8日に同端末で`RO-17`を実施し、実通知4件とウィジェット13件を合格とした。`WGT-014`は実広告の配置を確認し、2026年9月9日にversionCode `3`を機内モードで起動して広告非表示時も強制終了せず操作を継続できることを確認したため合格とした。
- 2026年9月9日にClosed testingフィードバックを反映したversionName `1.0.0`、versionCode `3`のAABを同じClosed testingトラックへ公開し、状態が`クローズドテスト公開開始`であることをUSERが確認した。新規警告はなかった。
- 同日にPixel 9a（Android 17 / API 37）でversionCode `2`から`3`へアンインストールせず更新し、既存データ、設定、通知、ウィジェット、バックアップおよび起動に問題がないことを確認した。
- versionCode 3変更内容の個別再試験はPixel 9a（Android 17 / API 37）で完了した。不足環境の確認は[残実機・環境試験計画](../../test-specs/release-v3-device-verification-plan.md)に従って実施する。
- versionCode `3`公開後もPre-launch report、SDK Indexおよびポリシー状態はversionCode `2`確認時から変わっていない。
- 対象アカウントの要件に従い、12人以上のテスターが14日間連続してオプトインした状態を維持する。
- テスターには試験項目を割り当てず、実際に複数日にわたって自由操作してもらい、利用状況と自由記述のフィードバックを収集する。
- 条件達成後、テスト方法、参加状況、フィードバック、修正内容を整理してProduction accessを申請する。

## 6. 未完了・保留

### 6.1 Closed testing中に完了する

- テスターのオプトインおよび14日間の継続参加
- テスターの端末・OS・利用期間・自由操作のフィードバックの記録
- Google PlayのSDK Index、権限申告およびポリシー警告の最終確認
- 利用規約と外部送信に関する公表について必要な専門家確認
- Data safety、UMP、SDK、実通信および公開法的文書の最終突合

必要人数は2026年9月9日時点で充足しており、14日間の継続参加を依頼中である。開始日と達成予定日はPlay Consoleの表示を確認して台帳へ記録する。

### 6.2 次の実変更で確認する

- versionCode `3`では全自動ゲート、Upload Key署名、成果物ハッシュ検査およびGoogle Play経由の上書き更新に合格済みである。変更内容固有の実機確認と不足環境の回帰試験を実施する。
- versionCode `3`公開後にアプリ、ビルド設定、法的本文またはPlay掲載成果物を変更する場合は、versionCodeを`4`以上へ上げて公開候補を再生成する。

### 6.3 外部状態待ち

- versionCode `3`公開後もPre-launch reportは`リリース前レポートを生成するにはアーティファクトをアップロードしてください`の表示から変わっていない。SDK関連警告はなく、ポリシー状態は`問題は見つかりませんでした`である。レポート生成だけを目的とする追加AABは登録せず、Console側の反映を待って再確認する。
- AdMobとGoogle Playのアプリ連携および実広告バナー表示は2026年9月7日に確認済みである。AdMob側の`app-ads.txt`検証状態、アプリ準備状況およびポリシー警告は最終公開判定時に再確認する。
- 本番広告を試験目的でクリックしない。

## 7. 本番公開前後の残りゲート

1. Closed testing要件を達成し、Production accessの承認を得る。
2. 公開対象commit、versionName、versionCode、リリースノートおよびAABを最終確定する。
3. 上表の`1.0.0 (3)`、ソースcommit、AAB SHA-256および保存済み成果物を再照合する。アプリまたは公開成果物を変更した場合はversionCodeを`4`以上へ上げ、クリーンな`main`から署名済み成果物を再生成して`node tools/release/verify-readiness.mjs --release`を成功させる。
4. 全P0/P1試験、Pre-launch report、権限、Data safety、SDK Indexおよび法的確認を完了する。
5. 初期配布地域を日本としてProductionへ公開する。
6. 公開後にGoogle Playからの新規インストール、Android vitalsおよびポリシー状態を確認する。
7. 公開日、最終AAB SHA-256、リリースノート、正本・公開サイトのcommitおよびGitタグを記録する。
