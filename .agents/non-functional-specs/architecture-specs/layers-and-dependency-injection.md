# レイヤー・依存性注入仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 親仕様: [実装アーキテクチャ・データアクセス仕様](README.md)

## 1. Gradleモジュール

- 初回公開までは`:app`の単一Android Applicationモジュールとする。
- package-private、`internal`、インターフェースとテストでレイヤー境界を維持する。
- ビルド時間、再利用、独立所有または循環依存の解消という計測可能な理由が生じた場合だけGradleモジュールへ分割する。
- モジュール分割自体を機能実装の前提にしない。

## 2. パッケージ

```text
com.mochisofts.mata
├── app/                 # Application、Activity、root navigation
├── core/
│   ├── common/          # Result、Dispatcher、Clock等
│   ├── designsystem/    # Theme、token、共通component
│   ├── navigation/      # 型付きroute、navigator
│   └── platform/        # OS API Adapter
├── data/
│   ├── local/           # Room、DataStore、entity、DAO、migration
│   ├── remote/          # 祝日DTOと通信
│   ├── repository/      # Repository実装
│   └── sdk/             # Ads、UMP Adapter
├── domain/
│   ├── model/           # Android非依存モデル
│   ├── repository/      # Repository interface
│   └── usecase/         # 共有される業務処理
├── ui/
│   ├── todolist/
│   ├── todoeditor/
│   ├── calendar/
│   ├── category/
│   ├── archive/
│   └── settings/
└── worker/              # Hilt WorkerとReceiverからの起動口
```

- パッケージ名は小文字だけとする。
- 画面固有のComposable、UiState、ViewModelを`ui/<feature>`へまとめる。
- 共通UIへ画面固有のRepositoryや状態遷移を依存させない。

## 3. 依存方向

- `ui`は`domain`と`core`へ依存できる。
- `data`は`domain`と`core`へ依存できる。
- `domain`はAndroid SDK、Compose、Room、DataStore、外部SDKへ依存しない。
- `domain`は`data`または`ui`を参照しない。
- `data`は`ui`を参照しない。
- OSとSDKの型をdomain modelまたはUiStateへ露出しない。

## 4. RepositoryとUseCase

- TODO、カテゴリ、履歴、設定、通知、祝日、広告同意ごとに責務が明確なRepositoryを定義する。
- Repository interfaceはdomain、実装はdataへ置く。
- 単純な読取を無条件にUseCaseへ包まず、複数Repository、繰り返し計算、トランザクションまたは複数画面共有がある処理をUseCaseとする。
- TODO完了、取消、アーカイブ、復元、完全削除、論理日整合はUseCaseを入口とする。
- RepositoryはEntityではなくdomain modelまたは専用Projectionを返す。
- 書き込みAPIは成功、業務エラー、競合、技術エラーを型で区別する。

## 5. Hilt

- `Application`へ`@HiltAndroidApp`、単一Activityへ`@AndroidEntryPoint`を使用する。
- ViewModelは`@HiltViewModel`とコンストラクタ注入を使用する。
- 所有クラスは原則コンストラクタ注入し、Field Injectionを避ける。
- Repository interface、Room DB、DAO、DataStore、Clock、Dispatcher、SDK AdapterをHiltで提供する。
- `SingletonComponent`はDB、DataStore、Repository、SDK接続管理などアプリ全体で1つの対象に限定する。
- Activityまたは画面をSingletonへ保持しない。
- WorkerはHilt対応Workerとし、Work入力からRepositoryを直接構築しない。
- BroadcastReceiverは短い入口とし、Singleton依存またはWorkManagerへ委譲する。

## 6. 注入する共通依存

- `Clock`
- 現在の`ZoneId`を提供するProvider
- Coroutine Dispatcher群
- UUID／operationId生成器
- Holidays API Client
- Alarm、Notification、Widget、Work Scheduler
- Ads、UMP Adapter
- バックアップStream Factory
- Logger

- `System.currentTimeMillis()`、`ZoneId.systemDefault()`、`Dispatchers.IO`を業務コードへ直接散在させない。
- 実装ではOS状態の最新値を取得し、テストでは固定値を注入する。

## 7. 外部SDK Adapter

- Ads、UMP、祝日通信、AlarmManager、NotificationManager、AppWidgetManagerをinterface越しに利用する。
- AdapterはSDK型、例外、コールバックをアプリ型、Flow、suspend関数へ変換する。
- コールバックを1回だけ完了させ、キャンセルとライフサイクル終了を処理する。
- SDKの生レスポンスをUI、domain、ログへ渡さない。
- Fakeは成功、遅延、取消、再試行可能失敗、恒久失敗、古い結果を再現できるようにする。
