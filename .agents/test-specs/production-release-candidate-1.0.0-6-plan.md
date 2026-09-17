# MATA 1.0.0 (6) 本番公開候補生成計画

- 対象: `com.mochisofts.mata` / `1.0.0 (6)`
- 状態: 未生成。Closed testingの14日間達成後に生成する
- 計画確定日: 2026-09-17
- Closed testing基準版: `1.0.0 (5)`
- リリース状況: [初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md)
- 公開判定基準: [リリースチェックリスト](../non-functional-specs/release-specs/release-checklist.md)
- 本番公開文案: [Google Playストア掲載文・画像仕様](../non-functional-specs/release-specs/store-listing-copy-and-assets.md)
- 差分・回帰範囲: [versionCode 5以降の差分棚卸し・versionCode 6回帰試験計画](version-6-delta-and-regression-plan.md)
- 回帰試験実施票: [MATA 1.0.0 (6) 回帰試験実施票](version-6-regression-results.md)
- 成果物台帳: [MATA 1.0.0 (6) 公開候補生成結果](release-candidate-1.0.0-6.md)

## 1. 候補の位置付け

- versionCode `5`はClosed testingの検証版とし、Productionへ昇格しない。
- versionCode `6`を初回Production向けの本番公開候補とする。versionNameは`1.0.0`を維持する。
- versionCode `6`の設定、署名済みAAB生成および成果物確定は、Play Consoleが12人以上・14日間連続のClosed testing要件達成を示した後に行う。
- 本番公開候補の生成はProduction公開の承認を意味しない。Production access、Console、実機および人的承認の各ゲートを別途完了する。
- 現在の`app/build.gradle`にあるversionCode `5`は、生成作業を開始するまで変更しない。

## 2. 生成開始条件

次をすべて確認してからversionCode `6`の作業を開始する。

- [ ] Play Consoleで12人以上・14日間連続のClosed testing要件達成を確認した。
- [ ] Closed testingの参加状況、自由操作、フィードバックおよび対応内容を台帳へ記録した。
- [ ] 本番公開へ反映するIssueとPull Requestがすべてmainへ取り込まれ、S0・S1の未解決障害がない。
- [x] versionCode `5`の基準commitから2026年9月17日時点のmainまで、ソース、依存関係、法的文書およびストア掲載成果物の差分を棚卸しし、versionCode `6`の回帰範囲を確定した。本番公開用の掲載文とversionCode `6`リリースノートも同日に正本と`fastlane/metadata`へ確定済みである。候補生成直前に棚卸し対象commit以降の差分を再確認する。
- [ ] Google PlayのSDK Index、権限、Data safety、ポリシー状態およびAdMob状態に新しい公開ブロッカーがない。
- [ ] 最新のmainがクリーンで、origin/mainと一致している。

Production accessは本番アップロード前の必須条件だが、審査待ちの間にversionCode `6`の候補生成とローカル検証を進めてもよい。

## 3. 生成手順

1. mainを最新化し、作業ツリーがクリーンであることを確認する。
2. `app/build.gradle`のversionCodeを`6`へ変更し、versionName `1.0.0`を維持する。
3. versionCode `6`用の日本語リリースノートを追加し、Play掲載情報と法的正本の差分を確定する。
4. versionCode変更をPull Requestで検証し、必須CIがすべて成功した状態でmainへマージする。
5. Upload Keyを使用できる安全なローカル環境で、クリーンなmainから署名済みRelease AABと関連成果物を生成する。
6. `node tools/release/verify-readiness.mjs --release`を実行し、全検査を成功させる。
7. AAB、R8 mapping、Manifest、SBOM、ライセンス一覧、検査結果、Git commit、容量、SHA-256およびUpload Key証明書SHA-256を同じversionCodeへ紐付けて保管する。
8. 実績値を[公開候補生成結果](release-candidate-1.0.0-6.md)へ、試験結果を[回帰試験実施票](version-6-regression-results.md)へ記録し、計画値と実際の成果物を区別する。

## 4. Productionへ登録する前の確認

- [ ] Production accessが承認されている。
- [ ] versionCode `6`の公開候補検査がすべて成功している。
- [ ] 全P0/P1 405件の合格状態を維持し、versionCode `5`以降の差分に応じた回帰試験を完了した。
- [ ] versionCode `5`から`6`への上書き更新でTODO、カテゴリ、履歴、設定、通知、ウィジェットおよびバックアップ互換性が維持される。
- [ ] versionCode `6`の新規インストール、起動、主要操作、通知、ウィジェット、バックアップ・復元および実広告表示に問題がない。
- [ ] Pre-launch reportに未解決の重大問題がなく、SDK Index、権限、Data safetyおよびポリシー状態を再確認した。
- [ ] Google Playへ登録するAABのSHA-256が、本書に紐付けた検証済み成果物と一致する。
- [ ] 対象国・地域、リリースノートおよび公開方法を最終承認した。

## 5. 候補差し替えルール

- versionCode `6`をGoogle Playへ一度もアップロードしていない場合に限り、候補を再生成できる。再生成前のAABは使用禁止として破棄し、最新のcommitとハッシュで証跡を作り直す。
- versionCode `6`をGoogle Playへアップロードした後にバイナリ、設定、法的本文または同梱する公開成果物を変更する場合は、versionCode `7`以上を使用する。
- 文書上の進行状況だけを更新し、AABへ含まれる内容が変わらない場合はversionCodeを上げない。
- Closed testing版versionCode `5`のAABを、versionCode `6`の代わりにProductionへ昇格しない。

## 6. 完了条件

- [ ] `1.0.0 (6)`の実成果物と生成結果文書が一意に対応している。
- [ ] Productionへ登録したAABと検証済みローカル成果物のSHA-256が一致している。
- [ ] Google Play経由の更新・新規インストールおよび最終実機確認が合格している。
- [ ] Consoleの審査・公開状態、警告、公開日時および公開後監視結果を初回リリース進行記録へ転記した。
- [ ] Productionで実際に公開したcommitへ、公開後に注釈付きタグ`v1.0.0`を付与した。
