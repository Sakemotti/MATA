# MATA デザインシステム仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 関連仕様: [アプリ全体仕様](../app-spec.md)、[開発ガイドライン](../development-guidelines.md)、[全画面仕様](../screen-specs/README.md)、[ウィジェット仕様](../widget-specs/README.md)、[起動仕様](../startup-specs/README.md)、[画面サイズ・適応レイアウト仕様](../adaptive-layout-specs/README.md)

## 1. 目的

MATAの画面、ダイアログ、ウィジェット、起動表示に共通する色、文字、形状、余白、Elevation、アイコン、コンポーネント、モーション、アクセシビリティを定義する。

画面ごとの機能仕様を変更せず、ライト、ダーク、Dynamic Color、フォント拡大、支援技術で一貫した表示と操作を提供する。

## 2. 文書構成

| 文書 | 内容 |
| --- | --- |
| [色・テーマ仕様](colors-and-themes.md) | ブランド、固定テーマ、Dynamic Color、状態色、カテゴリ色 |
| [文字・形状・寸法仕様](typography-shapes-and-dimensions.md) | Typography、Shape、余白、サイズ、Elevation |
| [コンポーネント・アイコン仕様](components-and-icons.md) | 共通コンポーネント、TODO、カレンダー、アイコン、アプリアイコン |
| [モーション・アクセシビリティ・試験仕様](motion-accessibility-and-testing.md) | モーション、並べ替え、Semantics、実装、視覚回帰試験 |

## 3. 基本方針

1. Jetpack Compose版Material Design 3を基調とする。
2. 安定版のMaterial 3コンポーネントを優先する。
3. Material 3 Expressiveは、安定版APIで既存の情報設計を変更しない範囲だけで採用する。
4. 実験的APIは代替手段がない場合だけ使用し、アプリ共通コンポーネント内へ隔離する。
5. 色、文字、形状、余白、Elevation、アニメーションを共通トークンとして管理する。
6. 画面固有コードへ色値、文字サイズ、角丸、Elevationを直接記載しない。
7. Materialコンポーネントの標準動作とアクセシビリティを不必要に上書きしない。
8. ブランド表現よりTODO情報の視認性と操作性を優先する。

## 4. ブランド

- ブランド名は「MATA」とする。
- ブランドの基準色は落ち着いた緑とする。
- 固定テーマのSeed Colorは`#386A20`とする。
- 補助色にはニュートラルグリーン、アクセントには青緑を使用する。
- 赤はエラー、期限超過、未達成、破壊的操作に限定する。
- グラデーション、立体的な光沢、常時表示される装飾背景は使用しない。

## 5. 実装原則

- `MataTheme`からColorScheme、Typography、ShapesおよびMATA固有の意味トークンを提供する。
- MaterialThemeに存在しないカテゴリ色と状態色はCompositionLocalで提供する。
- 共通トークンとテーマは`ui/theme`、共通コンポーネントは`ui/components`へ配置する。
- 共通コンポーネントはMaterial 3コンポーネントを基礎とし、Semanticsと標準余白を統一する。
- 画面固有の状態判定をデザインシステムへ持ち込まない。
- Previewからライト、ダーク、固定テーマ、フォント拡大を確認できるようにする。
- 使用中の実験的Material APIを一覧管理する。
- Materialライブラリ更新時に共通コンポーネントの見た目と操作を回帰確認する。

## 6. 公式資料

- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Composeのデザインシステム](https://developer.android.com/develop/ui/compose/designsystems)
- [Composeのアクセシビリティ](https://developer.android.com/develop/ui/compose/accessibility)
- [ComposeアクセシビリティのAPI既定値](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
