# Dependabot通常更新3件の確認（2026-11-10）

- 対象: AGP 9.5.1、UI Automator 2.4.1への更新と、KSP 2.3.13の採用判断
- ブランチ: `feature/dependabot-maintenance-20261110`
- 基点: main `3d83182b76293d9e5ecab9e5016fcb3b60395f34`
- 方針: 安全に採用できる2件の元commitを統合し、必須CI・会話解決・最新baseを確認して通常マージする。KSPは公式の既知不具合により保留し、成功扱い・形式的なクローズを行わない
- 公開版: [1.0.0 (11)](production-candidate-1.0.0-11.md)の保管済みAAB・署名・公開タグは変更しない

## 1. 対象とCI失敗原因

| 元PR | 更新・扱い | 調査時head・失敗対象 |
| --- | --- | --- |
| [#350](https://github.com/Sakemotti/MATA/pull/350) | AGP 9.5.0 → 9.5.1を統合 | `4e480c57c2f31df33adef5a03e2497df1671f63f`。plugin marker POMの検証情報未登録 |
| [#351](https://github.com/Sakemotti/MATA/pull/351) | UI Automator 2.4.0 → 2.4.1を統合 | `1d6bf6f7bf5a96de35b4b9877a45536fe850b20b`。benchmark構成のmoduleメタデータの検証情報未登録 |
| [#352](https://github.com/Sakemotti/MATA/pull/352) | KSP 2.3.12 → 2.3.13は保留 | `d89e19aeedecd87095d6c738188df367789a2d6d`。plugin marker POMの未登録に加え、公式に新版の回帰が告知されている |

元CIは[#350の実行](https://github.com/Sakemotti/MATA/actions/runs/42538949481)、[#351の実行](https://github.com/Sakemotti/MATA/actions/runs/42539196164)、[#352の実行](https://github.com/Sakemotti/MATA/actions/runs/42539276054)を確認した。過去の失敗やCodeQLのNEUTRALを成功へ読み替えず、採用する2件の統合構成で全CIを確認する。

## 2. 互換性・検証情報

- AGP 9.5系の最低条件はGradle 9.6.0・JDK 17。Wrapper 9.8.2、daemon JDK 21、USER更新済みのAndroid Studio Rabbit 2を維持し、IDE・グローバルGradle設定を変更しない。
- UI Automator 2.4.1はShellServerのポート衝突による起動失敗を修正した版である。本プロジェクトではbenchmarkモジュールの試験用依存であり、新しいアプリ機能・権限・外部データ送信を導入しない。
- 標準Gradleタスクでapp・benchmark両モジュールのロックと検証メタデータを生成し、SBOM・ライセンス生成が取得するPOMとリソース処理のAAPT2も確認した。
- 既存artifact 2,903件のSHA-256集合と検証設定は不変。artifactエントリ92件を追加した。追加はAGP関連とUI Automator関連だけで、KSP 2.3.13の検証情報を登録しない。
- AGPのplugin marker POM 2件、本体JAR、UI Automatorのmodule・AAR、AAPT2のWindows／Linux JAR・POMの計8件は、Google公式Mavenの配布物と公開SHA-256、登録値の一致を独立に照合した。CI用Linux AAPT2は`9.5.1-16666295`として個別登録した。
- 検証を`strict`のまま維持し、信頼ワイルドカードの拡張、checksum検査の無効化、依存関係の無関係な更新は行わない。

## 3. KSP更新の保留

2026-11-10時点で、[公式リリースノート](https://github.com/google/ksp/releases/tag/2.3.13)に並行するin-process実行時の回帰が告知されている。[上流Issue #3286](https://github.com/google/ksp/issues/3286)ではライブラリシンボルを解決できず、生成コードの欠落等が起こり得ると報告されている。

Issueは修正PR #3294でclosedだが、GitHubの最新正式リリースはまだ2.3.13だった。Issueのclosedを2.3.13へ修正配布済みという意味に読み替えない。MATA自身で同じ回帰を再現したとは扱わず、公式告知を根拠に現行2.3.12を維持する。

[#352の保留理由](https://github.com/Sakemotti/MATA/pull/352#issuecomment-6760037785)を記録し、PRはopenのまま残す。修正済み正式版の公開後に差分・互換性と全CIを再確認する。恒久的な並行処理の禁止やDependabotのignore追加で回避しない。

## 4. 検証と公開版の境界

ローカル検証は署名資格情報をコマンドラインの空値で上書きし、`MATA_REQUIRE_UPLOAD_SIGNING=false`、非公開候補の広告IDを使う。資格情報の読出し・出力や公開候補の再署名は行わない。PCの資源負荷を抑えるため、コマンドだけでワーカー2・並列実行なし・JVM上限3GBとCPU数2を指定する。

メタデータ生成後は書出しフラグなしの`--dependency-verification strict`で、単体試験・Debug／ReleaseのLint・Debug APK・instrumented APK・署名／法的URLガード・Release成果物・Performance APKを検証する。API 30のinstrumented testとCodeQLは統合PRの必須CIで確認する。実行結果と対象headは統合PRのコメント・チェックを正とし、過去のP0/P1 405件の合格を今回の全件再実施として扱わない。

保管済み公開版11のAAB SHA-256は`3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE`で不変だった。今回のビルド出力を保管済みAAB・ZIPへ複製しない。次回配布には、全トラックの登録済み最大番号より大きいversionCodeの別候補を作成し、カテゴリ並べ替え修正と主要操作の差分回帰・更新確認を行う。依存更新のCI成功だけで修正版配布や公開版の不具合解消を完了扱いにしない。

## 5. 公式資料

- [AGP 9.5.1・9.5系のリリースノートと互換性](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- [UI Automator 2.4.1リリースノート](https://developer.android.com/jetpack/androidx/releases/test-uiautomator#2.4.1)
- [KSP 2.3.13リリースノート・既知の回帰](https://github.com/google/ksp/releases/tag/2.3.13)
- [KSP上流Issue #3286](https://github.com/google/ksp/issues/3286)
