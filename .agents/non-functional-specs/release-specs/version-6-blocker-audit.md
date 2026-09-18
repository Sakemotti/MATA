# versionCode 6 リリースブロッカー監査票

- 対象: MATA `1.0.0 (6)` 本番公開候補
- 初回監査日: 2026-09-17
- 初回監査commit: `3adc1623c9b2e6d890c7331b7ed118aca14719bd`
- 状態: 現時点で即時ブロッカーなし。候補生成直前に再監査する
- 関連計画: [MATA 1.0.0 (6) 本番公開候補生成計画](../../test-specs/production-release-candidate-1.0.0-6-plan.md)

## 1. 目的

versionCode `6`の生成開始前とProduction登録前に、GitHub上の未処理作業、CI、秘密情報・署名素材、依存関係および外部Consoleの公開ブロッカーを同じ観点で確認する。外部状態は変化するため、本書の初回結果を最終判定へ流用せず、候補生成直前に再実行する。

## 2. 2026年9月17日の監査結果

| 項目 | 結果 | 判定 |
| --- | --- | --- |
| 作業ツリー | `main`はクリーンで`origin/main`と一致 | 合格 |
| 対象commit | `3adc1623c9b2e6d890c7331b7ed118aca14719bd` | 記録済み |
| 未解決GitHub Issue | 0件 | 合格 |
| 未解決Pull Request | 0件 | 合格 |
| 最新main CI | [Android CI run 35235533436](https://github.com/Sakemotti/MATA/actions/runs/35235533436)が対象commitで成功 | 合格 |
| 直近main CI | 5件すべて成功 | 合格 |
| Git管理下の署名・認証ファイル | `.jks`、`.keystore`、`.p12`、`.pfx`、Google service credentialの該当0件 | 合格 |
| 秘密情報パターン | private key、GitHub token、Google API keyの該当0件 | 合格 |
| 作業ディレクトリ内の署名・認証ファイル | build、`.gradle`、`.git`を除く対象に該当0件 | 合格 |
| Dependabot設定 | `.github/dependabot.yml`あり。Gradle・GitHub Actionsを毎週確認 | 合格 |
| Dependabot alerts | GitHub APIが`Dependabot alerts are disabled`を返却 | 件数判定不可・改善推奨 |
| Code scanning | GitHub APIが`no analysis found`を返却 | 件数判定不可・改善推奨 |
| Secret scanning | GitHub APIが`Secret scanning is disabled`を返却 | 件数判定不可・改善推奨 |
| GitHub vulnerability alerts | GitHub APIが`Vulnerability alerts are disabled`を返却 | 件数判定不可・改善推奨 |

GitHub標準の4項目は無効または解析実績がないため、「警告0件」とは記録しない。現在のCIはPull Requestとmain更新時に署名素材、service credential、private key、GitHub tokenおよびGoogle API keyの代表的なパターンを拒否する。また、Dependabot通常更新PR 5件は2026年9月16日にCI成功後マージ済みである。以上から現時点のリリース停止要因にはしないが、GitHub標準機能を有効化するまでは可視性の不足として扱う。

### 2.1 2026年9月18日のセキュリティ機能強化

| 項目 | 設定・初回結果 | 判定 |
| --- | --- | --- |
| Dependabot alerts | vulnerability alertsを有効化。open alert 0件 | 合格 |
| Dependabot security updates | 有効 | 合格 |
| Secret scanning | 有効。open alert 0件 | 合格 |
| Push protection | 有効 | 合格 |
| Secret scanning追加機能 | non-provider patternsとvalidity checksは現在の利用条件では無効のまま | 制約記録済み |
| CodeQL default setup | 有効。default query suite、remote threat model、standard runner、週次実行 | 合格 |
| GitHub Actions解析 | [CodeQL run 35297369056](https://github.com/Sakemotti/MATA/actions/runs/35297369056)で成功 | 合格 |
| JavaScript/TypeScript解析 | 同runで成功 | 合格 |
| Java/Kotlin解析 | CodeQL 2.27.0がKotlin 2.4.20未対応のため失敗し、GitHubの自動調整で解析対象から除外 | 制約記録済み |
| Code scanning open alert | 成功した解析対象について0件 | 合格 |

CodeQLのJava/Kotlin失敗はアプリの通常ビルドエラーではなく、CodeQL extractorの対応版上限によるものである。通常のAndroid CIは単体試験、Lint、Debug・Release・Benchmarkビルド、Manifest、アーキテクチャおよびリリース成果物検査に成功している。KotlinをCodeQL対応のためだけにダウングレードせず、GitHub側がKotlin 2.4.20以降へ対応した時点でJava/Kotlin解析を再度追加する。

## 3. 候補生成直前の再監査

versionCode `6`へ変更する直前に、次をすべて再確認する。

- [ ] `main`を最新化し、作業ツリーがクリーンで`origin/main`と一致している。
- [ ] 未解決IssueとPull Requestを取得し、S0・S1障害および本番へ反映予定の未マージ変更がない。
- [ ] 対象main commitのAndroid CIが全ジョブ成功している。
- [ ] Git管理下と作業ディレクトリに署名鍵、keystore、service credentialまたは秘密情報が混入していない。
- [ ] Dependabot alerts、security updates、code scanning、secret scanningおよびpush protectionの有効状態とopen alertを再確認する。有効化されていない項目は「0件」と記録しない。
- [ ] CodeQLのJava/Kotlin対応版を確認し、Kotlin 2.4.20以降がサポートされた場合は解析対象へ再追加する。未対応の場合は通常CI成功と制約を再記録する。
- [ ] Google Play ConsoleのSDK Index、権限、Data safety、ポリシー状態およびAdMob状態に新規ブロッカーがない。
- [ ] Closed testingの12人以上・14日間連続要件達成と、Production accessの状態を確認する。
- [ ] 監査対象commit、確認日時、CI run、Issue・PR件数、Console結果を本書へ追記する。

## 4. Production登録前の最終監査

署名済みversionCode `6`を生成した後、Productionへ登録する前に次を確認する。

- [ ] 候補生成後に対象commitへ新しいバイナリ影響変更がない。
- [ ] 署名済みAABと成果物台帳のSHA-256が一致する。
- [ ] `node tools/release/verify-readiness.mjs --release`が全検査に成功する。
- [ ] versionCode `6`回帰試験とGoogle Play経由の更新・新規インストール試験が合格する。
- [ ] Pre-launch report、SDK Index、ポリシー、Data safety、権限およびAdMobに未解決の重大問題がない。
- [ ] 未解決Issue・Pull Request、CIおよび秘密情報検査を再度確認し、最終結果を記録する。

## 5. GitHub標準セキュリティ機能の扱い

Dependabot alerts、security updates、secret scanning、push protectionおよびCodeQL default setupは有効状態を維持する。候補生成前とProduction登録前にopen alertを再取得し、重大または高重大度の未解決警告がある場合は公開判定を保留する。

CodeQLはGitHub ActionsとJavaScript/TypeScriptを検査する。Java/KotlinはGitHubが提供するCodeQLの対応状況を定期確認し、Kotlin 2.4.20以降へ対応するまでは通常CIを必須検査として維持する。secret scanningのnon-provider patternsとvalidity checksは利用可能になった時点で有効化を再試行する。
