# Baseline Profile / Macrobenchmark

このモジュールは、MATAのBaseline Profile・Startup Profile生成と性能計測に使用する。
本番APKには含まれない。

## 前提

- Android 13（API 33）以上の実機またはエミュレーターを接続する。
- 性能値を採用するときは、代表端末の同一機種・OS・ビルド・電源条件で測定する。
- `benchmark`ビルドは`release`を継承し、R8を有効にした非debuggable APKをdebug鍵で署名する。
- Macrobenchmarkの開始前にbenchmark専用Receiverがデータを初期化し、5カテゴリ・100件の毎日TODOを投入する。

## ビルド確認

```shell
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmarkRelease
```

## Baseline Profile / Startup Profile生成

```shell
./gradlew :app:generateBaselineProfile
```

生成結果は`app/src/release/generated/baselineProfiles/`へ保存される。ランチャー、通知、
ウィジェットからの起動経路をStartup Profileへ含め、TODO一覧の表示・スクロール経路を
Baseline Profileへ含める。

## Macrobenchmark実行

```shell
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

`StartupBenchmark`はコンパイルなしとProfile利用時のCold起動、ランチャーのWarm/Hot起動、
通知・ウィジェットからのCold起動を各10回測定する。`StartupTimingMetric`のTTIDと、アプリの
`reportFullyDrawn()`に基づくTTFDを確認する。`TodoListBenchmark`は固定データでTODO一覧を
往復スクロールし、フレーム時間を測定する。

JSON結果とPerfettoトレースは`benchmark/build/outputs/connected_android_test_additional_output/`
以下へ出力される。
