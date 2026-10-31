# Dependabot通常更新5件の統合検証

- 状態: 統合PR #332を全CI成功後に通常マージ済み。元PR5件はすべてMERGED、確認時の未解決PRは0件
- 記録日: 2026-11-01（ローカル検証・公式資料の照合は2026-10-31）
- ブランチ: `feature/dependabot-release-maintenance`
- 本番候補: [1.0.0 (11)の固定成果物](production-candidate-1.0.0-11.md)。今回の更新を含めない

## 1. 対象

| 元PR | 更新 | 主な確認範囲 |
| --- | --- | --- |
| [#310](https://github.com/Sakemotti/MATA/pull/310) | Paging 3.5.1 → 3.5.3 | 一覧、履歴、アーカイブ等のPaging処理と関連自動試験 |
| [#311](https://github.com/Sakemotti/MATA/pull/311) | AboutLibraries 15.2.0 → 15.3.0 | ライセンス情報の生成、読込、pluginとruntimeのロック整合 |
| [#324](https://github.com/Sakemotti/MATA/pull/324) | kotlinx.serialization 1.11.0 → 1.12.0 | JSON、バックアップ互換性、永続データの関連試験 |
| [#325](https://github.com/Sakemotti/MATA/pull/325) | GMA Next-Gen SDK 1.5.0 → 1.6.0 | 広告・同意の利用API、推移依存、権限、SDKデータ開示 |
| [#326](https://github.com/Sakemotti/MATA/pull/326) | Gradle Wrapper 9.8.1 → 9.8.2 | 公式配布SHA-256、Debug・Release・Performanceビルド |

5件のcommitを統合ブランチへ取り込み、共有するロックファイルと検証メタデータを更新した。個別PRの過去のCI失敗を合格へ書き換えず、統合後の全構成を通常CIで検証する。統合PRの成功・マージ後にのみ、元PRの包含関係と対応状態を確認する。形式的なクローズ、必須検査の省略、保護回避、force push、ブランチ削除は行わない。

## 2. 原因と対応

- ライブラリ更新PRは、新版のplugin・POM・module・AAR・JARに対するSHA-256未登録が主な失敗原因だった。Google Maven・Maven Central・Gradle Plugin Portalの既存リポジトリ設定を維持し、標準Gradleタスクで不足分を生成した。
- WrapperのSHA-256はGradle公式配布サイトと一致した。4ライブラリのPOMも各公式リポジトリが公表するチェックサムと一致した。
- 既存component・artifactのSHA-256集合と信頼ルールは変更せず、313行の検証メタデータを追加した。`strict`を無効化せず、新しい信頼ワイルドカードも追加しない。
- AboutLibrariesのruntime・pluginを15.3.0へ揃え、両モジュールのロックを再生成した。DBスキーマとバックアップ形式の変更はない。
- 広告SDKは、未レビュー版を拒否する固定検査が旧版1.5.0を要求していた。公式リリースノート・データ開示のレビュー後、main向けの固定値を1.6.0へ更新した。将来版を自動許可する正規表現には変更しない。
- Release Manifestの権限集合は、保管済みversionCode 11と一致した。新しい広告形式・SDK呼び出し・アプリ自身の送信経路は追加していない。

## 3. ローカル検証とCIの区別

2026-10-31に標準Gradleタスクでチェックサムとロックを生成し、次を実行した。署名資格情報はコマンドラインの空値で上書きし、Configuration Cacheなし・署名不要の検証構成とした。秘密値の読出し・出力、公開候補の再署名は行っていない。

- `testDebugUnitTest`: 240件、失敗・エラー・スキップ0件。
- `lintDebug`・`lintRelease`: 成功。
- `assembleDebug`・`assembleDebugAndroidTest`・`assembleBenchmark`・`:benchmark:assembleBenchmarkRelease`・`bundleRelease`: 成功。
- 法的サイト、Play掲載情報、試験割当・結果・自動証跡、アーキテクチャ、外部データ契約、リリースツールのNode検査: 成功。

上記のGradle生成実行は、チェックサム更新を伴うローカル検証である。追加登録後は書出しオプションを付けない通常の`strict`検査とPR CIで再検証し、API 30のinstrumented test、Release SBOM・Manifest・成果物検査も確認する。既存P0/P1 405件の過去の実機結果を今回の再実施とは扱わない。

2026-11-01に、書出しオプションなしの`strict`モードで全構成を再検証し、250タスクの実行が成功した。署名設定の異常系5件、Release Manifest・SBOM・5成果物ハッシュの検査も成功し、`node tools/release/verify-readiness.mjs --artifacts`は全5検査に合格した。生成物は`publishable=false`・署名なしの検証専用であり、本番候補へ流用しない。作業ツリーにも新しい差分はなかった。

更新後SBOMは226→228コンポーネント、ライセンス一覧は171→173ライブラリである。`androidx.javascriptengine:javascriptengine`、`com.google.android.gms:play-services-location`、`org.chromium.net:cronet`が追加され、`protobuf-javalite`が依存グラフから外れた。既存依存の版・artifact種別の更新も含めて照合した。権限集合は不変である。Chromiumのライセンス参照先は版別URLからChromium公式ソースのURLへ変わっており、過去と完全に同じライセンスJSONとは扱わない。

## 4. 本番候補との境界

本番へ使用するのは生成元commit `13af30baeec2c78de4661beda98535877585029c`、SHA-256 `3D2F763F6A33A87F3910835A0FB86764E1208A1970EBBA88BCFB89737D9F55EE`の保管済み11のAABである。再照合で変更がないことを確認した。今回のビルド出力をそのAABへ複製せず、GitタグやProductionリリースも作成しない。

更新後mainは本番候補11と異なる依存構成である。今回の更新を利用者へ配布する場合は12以上かつ全登録済み番号より大きい別候補を作り、広告・同意、ライセンス、JSON／バックアップ、Pagingの差分回帰とSDK開示の最終確認を行う。今回のCI成功を、11の未回答の原報告者再確認やConsole・AdMobの最終確認へ流用しない。

## 5. 公式資料

- [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html)
- [Paging](https://developer.android.com/jetpack/androidx/releases/paging)
- [AboutLibraries 15.3.0](https://github.com/mikepenz/AboutLibraries/releases/tag/15.3.0)
- [kotlinx.serialization 1.12.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0)
- [GMA Next-Genリリースノート](https://developers.google.com/admob/android/next-gen/rel-notes)
- [GMA Next-Genのデータ開示](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure)

## 6. CI・マージと元PRの確認

- 統合PR: [#332](https://github.com/Sakemotti/MATA/pull/332)。検証対象headは`9668a77bf22859c606f551b3c7ec9dbd93b37d6a`。
- [Android CI run 41249935282](https://github.com/Sakemotti/MATA/actions/runs/41249935282): Repository security、Debug、Release、Performance APK、最終集約は全成功。DebugのAPI 30 instrumented testも成功。
- [CodeQL run 41249932577](https://github.com/Sakemotti/MATA/actions/runs/41249932577): Actions・JavaScript/TypeScript解析と必須CodeQLチェックは成功。Java/Kotlin未対応の既知制約は別に保持する。
- 最新baseへの追従・必須チェック・未解決レビューなしと、元5件の最新headを包含していることを確認し、2026-11-01に通常マージした。mainのmerge commitは`efa207abae00479fd47833b2b23d2fafd4ff2174`。
- 元PR #310・#311・#324・#325・#326は、統合commitの取り込みによってすべて`MERGED`になった。個別に未検証のままクローズしたものではない。確認時の未解決PRは0件で、ブランチ削除や自動マージ設定は行っていない。
- 最新mainのCIとGitHubゲートは別途再照合し、Git除外の`app/build/outputs/release-metadata/github-release-gates.json`で対象commit・判定を確認する。元の生成時ゲートJSONやAAB・ZIPは上書きしない。

依存更新PR5件が残っているという公開準備上の保留は解消した。Productionへの送信・公開は未実施であり、R3-T16の再確認待ち、Pre-launch report、Console・AdMob・法的文書・掲載情報の最終確認と公開開始判断は残る。
