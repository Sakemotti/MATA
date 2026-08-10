# 色・テーマ仕様

- 文書状態: 確定
- 最終更新日: 2026-08-10
- 親仕様: [MATAデザインシステム仕様](README.md)
- 関連仕様: [カテゴリ管理画面仕様](../screen-specs/category-management/spec.md)、[カレンダー履歴画面仕様](../screen-specs/calendar-history/spec.md)

## 1. テーマ選択

- 設定値は「端末設定に従う」「ライト」「ダーク」の3種類とする。
- 初期値は「端末設定に従う」とする。
- テーマ変更はアプリ再起動なしで即時反映する。
- Android 12以上ではDynamic Colorを使用する。
- Android 11以下、Dynamic Color取得失敗時、Preview、スクリーンショットテストではMATA固定テーマを使用する。
- Dynamic Color専用のオン・オフ設定は設けない。
- カテゴリ色とMATA固有の状態色はDynamic Colorで変更しない。
- ダークテーマでは純黒を標準背景にせず、濃いニュートラルサーフェスを使用する。
- システムバーは現在のテーマとSurfaceに合わせ、アイコンの明暗も切り替える。

## 2. 固定ライトテーマ

| Role | 色 |
| --- | --- |
| `primary` | `#386A20` |
| `onPrimary` | `#FFFFFF` |
| `primaryContainer` | `#B7F397` |
| `onPrimaryContainer` | `#042100` |
| `secondary` | `#55624C` |
| `onSecondary` | `#FFFFFF` |
| `secondaryContainer` | `#D9E7CB` |
| `onSecondaryContainer` | `#131F0D` |
| `tertiary` | `#386668` |
| `onTertiary` | `#FFFFFF` |
| `tertiaryContainer` | `#BCEBED` |
| `onTertiaryContainer` | `#002022` |
| `error` | `#BA1A1A` |
| `onError` | `#FFFFFF` |
| `errorContainer` | `#FFDAD6` |
| `onErrorContainer` | `#410002` |
| `background` | `#FDFDF5` |
| `onBackground` | `#1A1C18` |
| `surface` | `#FDFDF5` |
| `onSurface` | `#1A1C18` |
| `surfaceDim` | `#DDDDD5` |
| `surfaceBright` | `#FDFDF5` |
| `surfaceContainerLowest` | `#FFFFFF` |
| `surfaceContainerLow` | `#F7F7EF` |
| `surfaceContainer` | `#F1F1E9` |
| `surfaceContainerHigh` | `#EBEBE4` |
| `surfaceContainerHighest` | `#E5E5DE` |
| `surfaceVariant` | `#DFE4D7` |
| `onSurfaceVariant` | `#43483F` |
| `outline` | `#74796E` |
| `outlineVariant` | `#C3C8BC` |
| `inverseSurface` | `#2F312C` |
| `inverseOnSurface` | `#F1F1EA` |
| `inversePrimary` | `#9CD67D` |
| `surfaceTint` | `#386A20` |
| `scrim` | `#000000` |

## 3. 固定ダークテーマ

| Role | 色 |
| --- | --- |
| `primary` | `#9CD67D` |
| `onPrimary` | `#0C3900` |
| `primaryContainer` | `#225106` |
| `onPrimaryContainer` | `#B7F397` |
| `secondary` | `#BDCBAF` |
| `onSecondary` | `#283420` |
| `secondaryContainer` | `#3E4A36` |
| `onSecondaryContainer` | `#D9E7CB` |
| `tertiary` | `#A0CFD1` |
| `onTertiary` | `#003739` |
| `tertiaryContainer` | `#1E4E50` |
| `onTertiaryContainer` | `#BCEBED` |
| `error` | `#FFB4AB` |
| `onError` | `#690005` |
| `errorContainer` | `#93000A` |
| `onErrorContainer` | `#FFDAD6` |
| `background` | `#1A1C18` |
| `onBackground` | `#E3E3DC` |
| `surface` | `#1A1C18` |
| `onSurface` | `#E3E3DC` |
| `surfaceDim` | `#1A1C18` |
| `surfaceBright` | `#40423C` |
| `surfaceContainerLowest` | `#141611` |
| `surfaceContainerLow` | `#22231F` |
| `surfaceContainer` | `#262823` |
| `surfaceContainerHigh` | `#30322D` |
| `surfaceContainerHighest` | `#3B3D37` |
| `surfaceVariant` | `#43483F` |
| `onSurfaceVariant` | `#C3C8BC` |
| `outline` | `#8D9387` |
| `outlineVariant` | `#43483F` |
| `inverseSurface` | `#E3E3DC` |
| `inverseOnSurface` | `#2F312C` |
| `inversePrimary` | `#386A20` |
| `surfaceTint` | `#9CD67D` |
| `scrim` | `#000000` |

## 4. 状態色

### 4.1 状態と役割

| 状態 | 色Role |
| --- | --- |
| 未完了 | `onSurface` |
| 進行中 | `secondaryContainer` / `onSecondaryContainer` |
| 完了・達成 | `statusSuccess` |
| スキップ | `surfaceVariant` / `onSurfaceVariant` |
| 未完了確定・未達成 | `errorContainer` / `onErrorContainer` |
| 期限超過 | `error` |
| 未来 | `outline` / `onSurfaceVariant` |
| 終了済み・アーカイブ | `surfaceVariant` / `onSurfaceVariant` |
| 外部処理保留 | `tertiaryContainer` / `onTertiaryContainer` |

### 4.2 成功色

| Role | ライト | ダーク |
| --- | --- | --- |
| `statusSuccess` | `#2E7D32` | `#9CD69A` |
| `onStatusSuccess` | `#FFFFFF` | `#003909` |
| `statusSuccessContainer` | `#B8F2B4` | `#15521E` |
| `onStatusSuccessContainer` | `#002204` | `#B8F2B4` |

### 4.3 表現規則

- 期限超過では期限時刻と「期限超過」ラベルだけをエラー色にし、TODO行全体を赤くしない。
- 完了では成功色、チェックアイコン、状態ラベル、取り消し線を併用する。
- スキップでは中立色、スキップアイコン、状態ラベルを併用する。
- 未達成ではエラー色、警告アイコン、状態ラベルを併用する。
- 進行中では補助色、進行中アイコン、状態ラベルを併用する。
- 状態を色だけで区別しない。

## 5. カテゴリ色

### 5.1 保存と表示

- 保存値は既存仕様どおり16種類の固定色IDとする。
- ライトテーマでは既存の基準色、ダークテーマでは同じ色IDに対応する明るい表示色を使用する。
- Dynamic Colorによってカテゴリ色を変更しない。
- 履歴へ保存されたカテゴリ色IDも、表示時のライト・ダークテーマに対応する色へ変換する。

### 5.2 テーマ別パレット

| 色ID | ライト | ダーク |
| --- | --- | --- |
| `red` | `#C62828` | `#EF9A9A` |
| `pink` | `#AD1457` | `#F48FB1` |
| `purple` | `#6A1B9A` | `#CE93D8` |
| `indigo` | `#283593` | `#9FA8DA` |
| `blue` | `#1565C0` | `#90CAF9` |
| `light_blue` | `#0277BD` | `#81D4FA` |
| `cyan` | `#00838F` | `#80DEEA` |
| `teal` | `#00796B` | `#80CBC4` |
| `green` | `#2E7D32` | `#A5D6A7` |
| `light_green` | `#558B2F` | `#C5E1A5` |
| `lime` | `#827717` | `#E6EE9C` |
| `yellow` | `#F9A825` | `#FFF59D` |
| `orange` | `#EF6C00` | `#FFCC80` |
| `deep_orange` | `#D84315` | `#FFAB91` |
| `brown` | `#5D4037` | `#BCAAA4` |
| `gray` | `#546E7A` | `#B0BEC5` |

### 5.3 前景色

- ライトテーマの`yellow`、`orange`、`deep_orange`上では黒を使用する。
- その他のライトテーマカテゴリ色上では白を使用する。
- ダークテーマのカテゴリ色上では黒を使用する。
- カテゴリ名の文字色にはカテゴリ色を使用せず、`onSurface`を使用する。

### 5.4 カテゴリ表示

- カテゴリアイコンはカテゴリ色の円形または角丸コンテナ内へ表示する。
- カテゴリ選択候補には色名、アイコン、チェックマーク、選択枠を表示する。
- カテゴリは名前とアイコンを併記し、色だけで識別させない。
- 「カテゴリ未設定」は`gray`と`CategoryOff`を使用する。

## 6. 受け入れ条件

1. 設定と端末状態に従ってライト・ダークテーマを即時切り替えられる。
2. Android 12以上でDynamic Color、利用不能時に固定テーマを使用できる。
3. Dynamic Color使用時もカテゴリ色と状態色を維持できる。
4. 固定テーマの全Color Roleが定義済みである。
5. 未完了、進行中、完了、スキップ、未達成、期限超過、未来、終了済みを色以外でも識別できる。
6. 16色すべてをライト・ダークテーマへ変換できる。
7. 各カテゴリ色上のアイコンと選択表示が必要なコントラストを満たす。
8. 現在と履歴のカテゴリ色IDを同じ規則で描画できる。
