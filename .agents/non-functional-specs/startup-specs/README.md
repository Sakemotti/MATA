# アプリ起動・初期化・復帰仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[開発ガイドライン](../../development-guidelines.md)、[TODO一覧画面仕様](../../screen-specs/todo-list/spec.md)、[通知仕様](../../functional-specs/notification-specs/README.md)、[ウィジェット仕様](../../functional-specs/widget-specs/README.md)、[MATAデザインシステム仕様](../design-system-specs/README.md)、[画面サイズ・適応レイアウト仕様](../adaptive-layout-specs/README.md)

## 1. 目的

MATAをランチャー、通知、ウィジェットから起動した場合、およびバックグラウンドやプロセス終了から復帰した場合の初期化順序、画面遷移、状態復元、障害回復、性能要件を定義する。

起動時の外部サービスやバックグラウンド処理によって、端末内のTODOを確認・操作できるまでの時間を不必要に延ばさない。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [起動・画面遷移仕様](launch-and-routing.md) | 起動経路、SplashScreen、初回表示、通知・ウィジェットからの遷移 |
| [初期化・復帰・障害回復仕様](initialization-and-recovery.md) | 初期化順序、復帰、プロセス終了、更新、再起動、エラー、安全性 |
| [性能・試験仕様](performance-and-testing.md) | TTID、TTFD、Baseline Profile、Startup Profile、試験条件 |

## 3. 基本方針

1. 単一ActivityとNavigation Composeを使用する。
2. 通常起動時の開始画面はTODO一覧とする。
3. 初回起動専用のオンボーディング、チュートリアル、利用登録画面は設けない。
4. サンプルカテゴリ、サンプルTODOは作成せず、システム上の「カテゴリ未設定」だけを利用可能にする。
5. ネットワーク処理や外部SDKの完了を待たず、端末内TODOを利用可能にする。
6. 各初期化、整合、復元処理は再実行されても結果が重複しないようにする。

## 4. 起動経路

次の経路を正式にサポートする。

- ランチャーアイコン
- 通知本文
- ウィジェットのヘッダー
- ウィジェットのカテゴリ見出し
- ウィジェットのTODOタイトル
- アプリがバックグラウンドにある状態からの復帰
- Activityまたはプロセス終了後の状態復元

外部Webリンク、他アプリからの共有、独自URLスキームによる起動は初期仕様では受け付けない。

## 5. 共通原則

- 起動しただけではTODOの完了、削除などの状態変更を行わない。
- 起動引数に含まれる表示情報を正として信用せず、識別子を使って端末内データベースから最新情報を取得する。
- 外部サービス障害は該当機能へ限定し、TODO一覧全体を利用不能にしない。
- ユーザー確認なしに端末内データを削除または初期化しない。
- 起動・復帰処理でTODO内容や広告情報を外部へ送信または診断ログへ記録しない。

## 6. 公式資料

- [AndroidX SplashScreen](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen)
- [NavigationのDeep Link](https://developer.android.com/guide/navigation/design/deep-link)
- [Direct Bootへの対応](https://developer.android.com/privacy-and-security/direct-boot)
- [アプリ起動時間](https://developer.android.com/topic/performance/vitals/launch-time)
- [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Startup Profile](https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations)
