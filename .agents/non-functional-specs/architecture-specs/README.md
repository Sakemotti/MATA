# 実装アーキテクチャ・データアクセス仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [開発ガイドライン](../../development-guidelines.md)、[ドメイン仕様](../../functional-specs/domain-specs/README.md)、[全画面仕様](../../screen-specs/README.md)、[エラー処理・障害回復仕様](../error-handling-specs/README.md)、[性能・省電力仕様](../performance-specs/README.md)

## 1. 目的

確定済み仕様を単一のAndroidアプリとして一貫して実装できるよう、レイヤー、依存性注入、状態管理、Navigation、Room、DataStore、ページング、検索、バックグラウンド処理およびテスト境界を定義する。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [レイヤー・依存性注入仕様](layers-and-dependency-injection.md) | モジュール、パッケージ、依存方向、Hilt、外部SDK Adapter |
| [UI状態・Navigation仕様](ui-state-and-navigation.md) | UDF、ViewModel、イベント、型付きルート、状態復元 |
| [データアクセス・ジョブ仕様](data-access-and-jobs.md) | Room、DataStore、Paging、検索、集計、WorkManager |
| [アーキテクチャ検証仕様](testing.md) | レイヤー試験、Fake、DB、Navigation、受け入れ条件 |

## 3. 基本方針

1. RoomとDataStoreをアプリデータのSingle Source of Truthとする。
2. UIはRepositoryやデータソースへ直接アクセスせず、ViewModelとUseCaseを介する。
3. UI状態は不変値として下へ流し、ユーザーイベントは上へ渡す単方向データフローとする。
4. 繰り返し、論理日、状態遷移、集計はAndroid UIから独立したdomain層へ置く。
5. 外部SDKとAndroid OS APIをAdapterで包み、Fakeへ置換可能にする。
6. 保存と状態変更はRepositoryがトランザクション境界を所有する。
7. 画面間にはIDと必要最小限の値だけを渡し、Entityやユーザー入力本文をBundleへ渡さない。
8. 初回実装は単一Gradleモジュールを維持し、パッケージ境界とテストで責務を分離する。

## 4. 採用技術

- Kotlin、Coroutines、Flow
- Jetpack Compose、Material 3
- Navigation Composeの型付きルート
- AndroidX ViewModel、SavedStateHandle、Lifecycle
- Hiltによる依存性注入
- RoomとRoom Migration
- Preferences DataStore
- Paging 3
- WorkManager、AlarmManager、AppWidgetManager
- Kotlin SerializationはNavigation引数と外部形式のDTOに限定して使用する

各ライブラリはVersion Catalogで一元管理し、実装時点の安定版を使用する。実験版またはPreview版は確定仕様を満たす安定版がない場合だけ、利用箇所と置換条件を記録して採用する。

## 5. 公式資料

- [Guide to app architecture](https://developer.android.com/topic/architecture)
- [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [Data layer](https://developer.android.com/topic/architecture/data-layer)
- [Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Save data with Room](https://developer.android.com/training/data-storage/room)
