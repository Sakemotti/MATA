# 性能・省電力仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [開発ガイドライン](../../development-guidelines.md)、[起動の性能・試験仕様](../startup-specs/performance-and-testing.md)、[データモデル仕様](../../functional-specs/domain-specs/data-model.md)、[通知仕様](../../functional-specs/notification-specs/README.md)、[ウィジェット仕様](../../functional-specs/widget-specs/README.md)

## 1. 目的

大量のTODOと履歴、日付境界、外部サービスおよびバックグラウンド処理が存在しても、主要操作の応答性、メモリ安全性、バッテリー効率および再現可能な性能計測を維持する。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [UI・データ性能仕様](ui-and-data-performance.md) | 応答時間、Compose、Room、メモリ、ページング |
| [バックグラウンド・省電力仕様](background-and-power.md) | WorkManager、AlarmManager、通知、ウィジェット、通信 |
| [計測・公開判定仕様](benchmark-and-release.md) | 基準データ、Macrobenchmark、回帰、公開条件 |

## 3. 基本方針

1. メインスレッドでネットワーク、データベース、ファイルI/Oまたは重い繰り返し計算を行わない。
2. 全件を必要としない画面では、ページング、期間指定または集約クエリを使用する。
3. 画面更新はRoomと設定の変更ストリームを正とし、一定間隔のポーリングを行わない。
4. 日付境界、次の期限、通知など必要な時刻だけを予約し、常駐サービスを使用しない。
5. 処理時間を隠すためにデータ整合性や表示内容を削らない。
6. 性能改善は管理された同一条件で計測し、体感だけで判断しない。
7. DebugとReleaseの差を考慮し、公開判定はR8とBaseline Profileを適用したRelease相当ビルドで行う。

## 4. 役割分担

- UI: 表示に必要な最小状態を購読し、安定したキーで差分描画する。
- ViewModel: 要求の取消、debounce、古い結果の破棄、状態集約を行う。
- Repository: クエリ範囲、トランザクション、ページング、キャッシュを管理する。
- domain: 純粋関数として繰り返しと論理日計算を行い、同じ値を再利用できるようにする。
- Worker・Scheduler: OS制約に従い、永続的な再試行と次回実行時刻を管理する。

## 5. 公式資料

- [App performance guide](https://developer.android.com/topic/performance)
- [Inspect app performance with Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Background work](https://developer.android.com/develop/background-work)
- [Core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)
