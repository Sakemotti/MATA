# 初回公開 静的アーキテクチャレビュー

- 実施日: 2026-09-07
- 対象: `REL-011`〜`REL-014`
- 対象版: versionCode 1
- 実施者: RELEASE_OWNER
- 自動検査: `node tools/test-specs/verify-architecture.mjs`

## 結論

4項目とも合格とする。レビューで見つかった逆向き依存と業務計算の配置を是正し、同じ違反をCIで検出する静的検査を追加した。

## REL-011 技術構成

| 確認対象 | 根拠 | 判定 |
| --- | --- | --- |
| Kotlin / AGP | Version CatalogのAGPは9.3.2。Android Kotlin pluginを適用せず、AGP 9の内蔵Kotlinを使用 | 合格 |
| Compose / Material 3 | Compose compiler plugin、Compose BOM、`androidx.compose.material3`を使用 | 合格 |
| Activity | 通常の画面遷移は`MainActivity`だけが担当 | 合格 |
| 補助Activity | `WidgetTodoActionActivity`はウィジェット操作と広告表示専用。非exported、履歴対象外であり、通常Navigationを持たない限定的な例外 | 合格 |
| 非同期 / 日時 | Coroutines / Flowと`java.time`を使用。旧`Date`、`Calendar`、ThreeTenは使用しない | 合格 |

## REL-012 レイヤーと保存経路

レビュー時に次を是正した。

- Android `Activity`を受け取る広告同意境界を`domain`から`core.ads`へ移し、domainをAndroid非依存へ戻した。
- 設定UIが`data.backup`の実装と状態型を直接参照していたため、`core.backup.BackupGateway`越しに変更した。
- 画面群が`app`の共通ナビゲーションUIへ依存していたため、`core.designsystem.navigation`へ移した。
- カテゴリ未設定を表す共有キーを`core.navigation`へ移した。

是正後の自動走査結果は次のとおり。

- domainからAndroid SDK、Compose、Room、DataStore、外部SDK、app/data/ui/widgetへの禁止import: 0件
- dataからuiへのimport: 0件
- uiからapp/dataへのimport: 0件
- ScreenからRepository、Room、DataStoreへの直接import: 0件
- 保存操作はComposableからViewModelへイベントとして渡し、ViewModelからRepositoryまたはプラットフォームGatewayを呼ぶ。

`app`はComposition Rootと起動調停、`widget`およびReceiverはOSからの入口、`data`はRoom・DataStore・外部SDK Adapterとして扱う。これらの入口での具象型参照は画面UIからの保存処理とは分離されている。

## REL-013 業務計算と状態公開

- 論理日、論理日開始・終了、期限、繰り返し日、回数期間は`domain/model/TodoScheduleCalculator.kt`に配置されている。
- TODO一覧に残っていた論理日境界を跨ぐ期限順計算を`Todo.effectiveDueSortMinutes`としてdomainへ移した。
- 4時開始の論理日における4:00、23:00、翌3:00、期限なしの順序を単体テストへ追加した。
- 主要UiState 9個はいずれも`data class`の`val`だけで構成され、`StateFlow`として公開される。
- 画面は`collectAsStateWithLifecycle`で購読し、操作はViewModelの公開イベント、単発結果はEffectとして扱う。

## REL-014 コード規約と表現

- `.editorconfig`でUTF-8、LF、末尾改行、Kotlin系4空白を指定し、`.gitattributes`でLFへ正規化する。
- main/testのKotlinと主要Gradleファイル156件について、CRLF、末尾改行欠落、タブインデントがないことを自動確認した。
- 全画面ソースについて、`Text`とcontent descriptionの直書き文字列、UIの色リテラル、データアクセスimportがないことを自動確認した。
- 色・Typography・Shapeは`MaterialTheme`と`core.designsystem`を使用する。
- 完了、スキップ、未完了、期限超過などは色だけでなく、文字列リソース、アイコン、content descriptionまたはstate descriptionを併用する。
- 論理日・繰り返し・期限等の複雑な規則はdomainの専用テストで保護する。

## 実行コマンド

```text
node tools/test-specs/verify-architecture.mjs
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache
```

両コマンドの成功に加え、Pull Request CIのDebug、Release、Performance APK、Repository securityおよびAPI 30 instrumented testが成功した時点で最終証跡とする。
