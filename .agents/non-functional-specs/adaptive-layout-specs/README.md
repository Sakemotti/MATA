# 画面サイズ・適応レイアウト仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [アプリ全体仕様](../../app-spec.md)、[開発ガイドライン](../../development-guidelines.md)、[全画面仕様](../../screen-specs/README.md)、[MATAデザインシステム仕様](../design-system-specs/README.md)、[収益化仕様](../../functional-specs/monetization-specs/README.md)

## 1. 目的

MATAをスマートフォン、タブレット、折りたたみ端末、ChromeOS、デスクトップウィンドウおよびマルチウィンドウで利用できるようにし、現在のウィンドウサイズと端末姿勢に応じたレイアウト、ナビゲーション、入力方法および状態保持を定義する。

Android Large Screen App QualityのTier 2「Large screen optimized」を初回公開の品質目標とする。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [ウィンドウ分類・ナビゲーション仕様](window-classes-and-navigation.md) | 対応範囲、サイズクラス、余白、コンテンツ幅、ナビゲーション |
| [画面別適応レイアウト仕様](screen-layouts.md) | 7画面と共通UIの幅別構成 |
| [Insets・折りたたみ・入力仕様](insets-foldables-and-input.md) | Edge-to-edge、IME、ヒンジ、キーボード、ポインター、状態復元 |
| [試験・公開判定仕様](testing-and-release.md) | 境界値、端末、入力、アクセシビリティ、公開条件 |

## 3. 基本方針

1. 端末種別や縦横方向ではなく、現在表示中のウィンドウの幅・高さでレイアウトを決定する。
2. Activityをサイズ変更から除外せず、縦横どちらでも利用できるようにする。
3. 幅が広いだけで情報をグリッドへ分割せず、作業効率が上がる画面だけを2ペイン化する。
4. 文字、表示サイズ、システムバー、ディスプレイカットアウト、IMEおよび折り目を考慮し、情報や操作を隠さない。
5. サイズ変更でデータを再取得せず、選択、入力、スクロール、ダイアログおよび処理状態を維持する。
6. タッチ、キーボード、マウス、トラックパッド、TalkBackおよびスイッチアクセスから主要機能を操作できるようにする。

## 4. 対象と対象外

### 4.1 対象

- Androidスマートフォンとタブレット
- ブック型・縦折り型を含む折りたたみ端末
- ChromeOS上のAndroidアプリ
- Androidのデスクトップウィンドウと自由形式ウィンドウ
- 分割画面、フリーフォーム、外部ディスプレイ
- 縦画面と横画面

### 4.2 対象外

- Wear OS、Android TV、Android Auto専用UI
- Picture-in-Picture
- デュアルスクリーン固有機能、背面ディスプレイ固有機能、テーブルトップ姿勢専用UI
- 同一アプリ内の複数ウィンドウ・複数インスタンスを前提とした機能

## 5. 実装原則

- `WindowSizeClass`はActivityの現在のウィンドウ領域から算出する。
- 画面方向、端末モデル、物理画面サイズをレイアウト切り替え条件にしない。
- サイズクラスはルートで監視し、ナビゲーションと各画面へ単一の適応情報として渡す。
- `android:screenOrientation`、固定アスペクト比、`resizeableActivity=false`で表示を制限しない。
- 構成変更は原則としてシステムへ処理させ、安易な`android:configChanges`指定でActivity再生成を回避しない。
- UI状態はViewModel、`rememberSaveable`、`SavedStateHandle`を役割に応じて使い分ける。

## 6. 公式資料

- [Large screens overview](https://developer.android.com/guide/topics/large-screens)
- [Window size classes](https://developer.android.com/develop/ui/views/layout/use-window-size-classes)
- [Canonical layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts)
- [Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)
- [Configuration and continuity](https://developer.android.com/guide/topics/large-screens/configuration-and-continuity)
- [Support different display sizes](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)
