# ログ・診断・品質監視仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [セキュリティ・プライバシー仕様](../security-privacy-specs/README.md)、[性能・省電力仕様](../performance-specs/README.md)、[エラー処理・障害回復仕様](../error-handling-specs/README.md)、[開発ガイドライン](../../development-guidelines.md)

## 1. 目的

障害と性能退行を調査できる最小限の診断情報を定義し、TODO内容や広告識別子などのユーザーデータをログや外部監視へ漏らさない。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [ログ・診断データ仕様](logging-and-diagnostics.md) | レベル、イベント、禁止項目、Debug・Release差分 |
| [クラッシュ・品質監視仕様](crash-and-quality-monitoring.md) | Android vitals、Pre-launch report、分析SDK方針、運用 |
| [検証・受け入れ仕様](testing.md) | ログ検査、難読化、品質ゲート、受け入れ条件 |

## 3. 基本方針

1. アプリ独自の利用分析、行動追跡、ユーザープロファイルを収集しない。
2. 初回公開ではFirebase Crashlytics等の第三者クラッシュ送信SDKを導入しない。
3. 公開後のクラッシュ、ANR、過度な起動、wake lockはGoogle Play ConsoleのAndroid vitalsを主要情報源とする。
4. ローカルログは障害の場所と分類を特定できる最小限にし、ユーザー入力と外部サービスの機密値を含めない。
5. Debugは開発診断を優先し、Releaseは警告・エラーと重要な状態遷移だけに制限する。
6. ログを処理の正データ、監査証跡、再試行キューとして使用しない。
7. 新しい監視SDKを追加する場合は、導入前にユーザー同意、データフロー、Data safety、ポリシーを再策定する。

## 4. 観測対象

- 起動経路、初期化段階、TTID、TTFD
- Roomオープン、マイグレーション、トランザクション分類
- TODO状態変更の成功・失敗分類
- 通知の登録・取消・発火・整合件数
- ウィジェット更新の契機・結果
- 祝日取得のHTTP分類・キャッシュ結果
- バックアップと復元の段階・件数・結果
- UMPと広告の状態分類
- WorkManagerの開始・再試行・終了
- 未捕捉例外、クラッシュ、ANR、性能退行

ユーザーが入力した値そのものではなく、固定イベント名、処理件数、所要時間、成功・失敗分類だけを観測する。

## 5. 公式資料

- [Android vitals](https://developer.android.com/topic/performance/vitals)
- [Diagnose and fix crashes](https://developer.android.com/topic/performance/vitals/crash)
- [Diagnose and fix ANRs](https://developer.android.com/topic/performance/vitals/anr)
- [Pre-launch reports](https://support.google.com/googleplay/android-developer/answer/9842757)
