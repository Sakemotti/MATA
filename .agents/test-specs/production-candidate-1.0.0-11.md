# MATA 1.0.0 (11) 本番公開候補の確定・昇格確認

- 状態: 審査中（2026-11-01 USER確認）。製品版本体を含む送信操作は確認済み、本番候補11は不変。本審査完了・承認・一般公開は未確認
- 確定日・確認元: 2026-10-31、USERが現在の最新を本番公開候補とすることを了承
- 最終更新日: 2026-11-01
- 成果物の生成記録: [Closed testing候補生成結果](release-candidate-1.0.0-11.md)
- 配布後の確認: [versionCode 11登録・更新確認手順](closed-testing-release-1.0.0-11.md)
- 公開手順: [初回Production公開実行票](../non-functional-specs/release-specs/initial-production-runbook.md)
- 外部最終確認: [Console・AdMob・掲載情報の確認票](production-console-final-check-1.0.0-11.md)

## 1. 固定する成果物

| 項目 | 確定値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `11` |
| AAB生成元commit | `13af30baeec2c78de4661beda98535877585029c` |
| 選定時のmain | `2a74058319b95325684afa34dd77cffeeac04a98`。AAB生成元とは区別する |
| AAB | `app/release/1.0.0-11/mata-1.0.0-11.aab` |
| 容量 | 13,084,547 bytes |
| SHA-256 | `3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| 採用方法 | Closed testingへ登録済みの同じ11のAABをProductionへ昇格またはライブラリから追加する。再ビルド・同じ番号の再アップロードは行わない |

選定時mainとの差分は文書、ストア画像と掲載マニフェスト、benchmark専用の撮影データであり、その時点では本番ソース・リソース、Release設定、Gradle設定・Wrapper、依存関係、DBスキーマとバックアップ形式に変更はなかった。`app/src/benchmark`のデータ投入処理はReleaseへ含まれない。候補確定後に本番向け変更を候補へ取り込む場合は、11へ後付けせず、12以上かつ全登録済み番号より大きいversionCodeで別候補を作り、差分試験と識別情報を更新する。

2026-11-01に[統合PR #332](https://github.com/Sakemotti/MATA/pull/332)を全CI成功後に通常マージし、次回開発向けmainへ5件の依存更新を取り込んだ。本番候補11は上記AABに固定し、新しい依存・ツールを含めない。更新後のmainと11の生成元は同一のRelease構成ではなく、mainから新しくビルドして11の保管物を置き換えることは禁止する。現在のmainのCI・GitHubゲートと、固定した11の生成元CI・配布後確認を別々に追跡する。

## 2. 2026-10-31の再照合

- ローカルmainがクリーンで`origin/main`と一致すること、生成元からの全変更一覧とRelease入力差分なしを確認した。
- 保管したAAB・mapping・ライセンス・Manifest・SBOMの5成果物は、生成時メタデータの容量・SHA-256と一致した。
- メタデータ、Release事前検査、生成時GitHubゲートの3ファイルと保管ZIPも、[生成記録](release-candidate-1.0.0-11.md)のハッシュと一致した。生成時の未合格結果を上書きしていない。
- `jarsigner -verify`の終了コードは0、`keytool -printcert -jarfile`で上記Upload Key SHA-256と一致した。既存記録と同種の証明書チェーン・自己署名・タイムスタンプ・POSIX属性・JarFile／JarInputStream差異の診断は維持し、無視して署名を変更する操作は行わない。
- 今回は再ビルドやAndroid試験の再実施ではない。生成元CIと既存試験、配布後のUSER確認を参照し、今回実行したハッシュ・差分・署名確認と区別する。

## 3. 公開可否のゲート

| 項目 | 状態・証跡 |
| --- | --- |
| Production access | 承認済み（2026-10-31 USER報告）。アプリ本体の一般公開は別 |
| 候補の技術的検査 | 生成時のRelease事前検査7件と生成元CIは合格。今回5成果物と保管証跡・署名を再照合 |
| P0/P1 | 既存結果405/405件合格。今回の全件再実施ではない |
| 10→11の更新・OWNER回帰 | 2026-10-29の一括報告で問題なし（CT-029）。端末・OS・実施日時・条件別詳細は未記録 |
| 新規インストール環境での確認 | 2026-11-01にUSERがversionCode 11の起動・TODO登録／完了・通知・ウィジェット・バックアップ／復元を確認済み・問題なしと回答（CT-030）。環境・実施日時・個別所見は未記録 |
| 広告位置の原報告者再確認 | 2026-11-01にUSERが「これはもう大丈夫」と回答し、再確認待ちの公開保留を解除した。個別の再確認詳細は未記録で、試験合格を追加しない |
| GitHubゲート | 選定時mainでは未解決PR5件により未合格だった。その5件は2026-11-01にPR #332で対応済み。外部最終確認開始時main `521a70457bac9103d8df670291d409eb3f9ac237`の監査は合格した。選定時の判定を上書きせず、公開直前にも最新mainを再照合する。Java/Kotlin CodeQL未対応の警告は維持 |
| Console・AdMob・公開Web | 2026-11-01に公開Webは正本一致・取得成功、Console主要項目と画像以外の掲載情報はUSER確認済み。その後、最新画像もConsoleへ反映したとUSER報告。Pre-launch reportは表示なし・結果未確認。AdMobは新規警告・配信制限なし、UMP公開済みとのUSER回答。ストア連携は一般公開後へ引き継ぎ、アプリ承認済みとは扱わない。詳細は外部最終確認票を参照 |
| 成果物の別保管 | 2026-11-01にUSERが候補ZIPをPC故障に備えた別の安全な場所へコピー済みと回答 |
| 公開の最終方針了承 | 2026-11-01にUSERがレポート未確認での公開判断、AdMobの一般公開後対応と審査中の配信制限の可能性、日本での初回公開と自身による24時間監視・問題時対応判断を了承。既登録11の製品版審査送信手順へ進む。実際の送信・一般公開は別に記録する |
| その後の審査送信 | 当初は日本追加のみの表示。その後USERが本体を未送信だったため再送信したと報告し、最新画像で製品版1.0.0の「完全公開を開始」と日本追加、クイックチェック中を確認。本体を含む送信操作の時刻は未記録で、最初の操作時刻13:25を転用しない。画像はversionNameのみでcode欄は写っていない。本審査開始・承認・一般公開は未確認 |
| 最新の審査状態 | 2026-11-01にUSERが本審査完了との報告を「まだ審査中でした」と訂正。現在は審査中、本審査完了・承認・一般公開は未確認。送信後の画像は当時のクイックチェックの証跡として保持し、未取得の最新状態画像・日時・警告を補完しない |

GitHub監査は`node tools/release/verify-github-release-gates.mjs`で実施し、結果はGit除外の`app/build/outputs/release-metadata/github-release-gates.json`へ保存する。選定時main、外部最終確認時の最新mainとAAB生成元を区別し、生成時の保管済みJSONは上書きしない。

選定時に未解決だった通常Dependabot PRは[#310](https://github.com/Sakemotti/MATA/pull/310)、[#311](https://github.com/Sakemotti/MATA/pull/311)、[#324](https://github.com/Sakemotti/MATA/pull/324)、[#325](https://github.com/Sakemotti/MATA/pull/325)、[#326](https://github.com/Sakemotti/MATA/pull/326)である。2026-11-01の統合マージ後に全5件が`MERGED`であることを確認した。候補への自動反映、形式的なクローズやゲートの緩和は行っていない。詳細は[統合検証記録](dependabot-maintenance-2026-11-01.md)を参照し、選定時の未合格結果は履歴として維持する。

## 4. 引き渡しと公開前の残確認

1. 依存更新PR5件の対応は完了。公開直前に最新のGitHubゲートを再判定する。
2. R3-T16の公開保留はUSER判断で解除済み。Pre-launch reportは未確認のまま既存試験結果を基に公開判断する方針をOWNERが了承した。レポート自体を合格へ変えない。
3. 最新画像のConsole反映と候補ZIPの安全な別保管、versionCode 11の新規インストール環境での主要確認はUSER確認済み。AdMobの一般公開後確認への引き継ぎ・配信制限の可能性と、監視・対応担当もOWNER了承済み。確認済み事項と未確認事項を分けて維持する。
4. 全ゲートの完了とOWNERの公開開始判断後、Productionで既登録の`1.0.0 (11)`を選択する。再アップロードや新しい署名鍵の設定は行わない。
5. 初回Production用の掲載文仕様第4.2節をリリースノートに使用する。`changelogs/11.txt`はClosed testingの修正告知履歴として保持し、公開原稿と混同しない。
6. Consoleの審査・公開状態と既知警告を実際の文言で記録し、公開後の新規インストールと監視を行う。`v1.0.0`タグは実際の公開確認後にAAB生成元commitへ付与する。

既登録AABの再利用は[Google Playの公式手順](https://support.google.com/googleplay/android-developer/answer/9859348?hl=ja)に従う。今回こちらからConsole操作やテストトラックの停止は行っていない。USERの送信操作と画像で確認できるクイックチェックを、未確認の本審査開始・承認・一般公開と区別する。
