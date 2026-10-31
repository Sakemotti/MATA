# MATA 1.0.0 (11) 本番公開候補の確定・昇格確認

- 状態: 候補確定。公開ゲートは未合格、Productionへの登録・審査送信・一般公開は未実施
- 確定日・確認元: 2026-10-31、USERが現在の最新を本番公開候補とすることを了承
- 成果物の生成記録: [Closed testing候補生成結果](release-candidate-1.0.0-11.md)
- 配布後の確認: [versionCode 11登録・更新確認手順](closed-testing-release-1.0.0-11.md)
- 公開手順: [初回Production公開実行票](../non-functional-specs/release-specs/initial-production-runbook.md)

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

今回のDependabot整理では、次回開発向けmainへ5件の依存更新を統合する。本番候補11は上記AABに固定し、新しい依存・ツールを含めない。更新後のmainと11の生成元は同一のRelease構成ではなく、mainから新しくビルドして11の保管物を置き換えることは禁止する。現在のmainのCI・GitHubゲートと、固定した11の生成元CI・配布後確認を別々に追跡する。

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
| 広告位置の原報告者再確認 | R3-T16の回答待ち。候補確定によって完了にはしない |
| GitHubゲート | **未合格**。選定時mainに対する監査で、未解決PR5件だけが失敗。未解決Issue・セキュリティalertは各0件、main CIと保護設定は合格。Java/Kotlin CodeQL未対応の警告は維持 |
| Console・AdMob・公開Web | 公開前の最終照合は未完了。Pre-launch reportは結果未確認。最新ストア画像のConsole差し替え完了も未確認 |
| 公開の最終承認 | 未実施。候補選定の了承と公開開始の了承を分ける |

GitHub監査は`node tools/release/verify-github-release-gates.mjs`で実施し、結果はGit除外の`app/build/outputs/release-metadata/github-release-gates.json`へ保存した。対象は選定時mainであり、AAB生成元の監査結果と混同しない。

選定時に未解決だった通常Dependabot PRは[#310](https://github.com/Sakemotti/MATA/pull/310)、[#311](https://github.com/Sakemotti/MATA/pull/311)、[#324](https://github.com/Sakemotti/MATA/pull/324)、[#325](https://github.com/Sakemotti/MATA/pull/325)、[#326](https://github.com/Sakemotti/MATA/pull/326)である。これらは統合検証後にmainへ取り込む方針とし、候補への自動反映、形式的なクローズやゲートの緩和は行わない。統合後に元PRの対応状態と最新ゲートを再確認するまでは、Productionへの送信・公開を行わない。選定時の未合格結果は履歴として維持する。

## 4. 引き渡しと公開前の残確認

1. 未解決PRを別途対応し、最新のGitHubゲートを再判定する。
2. R3-T16の広告位置再確認待ちの扱いと、未確認の試験・レポートについて最終公開判断を記録する。未回答を合格へ変えない。
3. Productionページ、地域（日本）、無料・商品なし、App content、SDK Index、Data safety、ポリシー、AdMob、公開法的文書と掲載文・画像を最終確認する。
4. 全ゲートの完了とOWNERの公開開始判断後、Productionで既登録の`1.0.0 (11)`を選択する。再アップロードや新しい署名鍵の設定は行わない。
5. 初回Production用の掲載文仕様第4.2節をリリースノートに使用する。`changelogs/11.txt`はClosed testingの修正告知履歴として保持し、公開原稿と混同しない。
6. Consoleの審査・公開状態と既知警告を実際の文言で記録し、公開後の新規インストールと監視を行う。`v1.0.0`タグは実際の公開確認後にAAB生成元commitへ付与する。

既登録AABの再利用は[Google Playの公式手順](https://support.google.com/googleplay/android-developer/answer/9859348?hl=ja)に従う。公開操作とテストトラックの停止は今回実施していない。
