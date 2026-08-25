# MATA 画面仕様

このディレクトリでは、MATAの画面ごとの詳細仕様を管理する。

全画面に共通するプロダクト要件は、[アプリ全体仕様](../app-spec.md)を正とする。画面仕様とアプリ全体仕様が矛盾する場合は、アプリ全体仕様を更新したうえで画面仕様へ反映する。

全画面に共通する色、文字、形状、寸法、コンポーネント、アイコン、モーションおよびアクセシビリティは、[MATAデザインシステム仕様](../non-functional-specs/design-system-specs/README.md)を正とする。

全画面に共通するウィンドウサイズ、ナビゲーション形式、最大幅、2ペイン、Insets、折りたたみ端末およびキーボード・ポインター操作は、[画面サイズ・適応レイアウト仕様](../non-functional-specs/adaptive-layout-specs/README.md)を正とする。

全画面に共通するエラー分類、表示手段、文言、再試行、縮退および障害回復は、[エラー処理・障害回復仕様](../non-functional-specs/error-handling-specs/README.md)を正とする。画面固有の確定済み規則がある場合は画面仕様を優先する。

## 画面一覧

| ID | 画面 | 仕様 |
| --- | --- | --- |
| SCR-001 | TODO一覧 | [todo-list/spec.md](todo-list/spec.md) |
| SCR-002 | TODO登録・編集 | [todo-editor/spec.md](todo-editor/spec.md) |
| SCR-003 | カレンダー履歴 | [calendar-history/spec.md](calendar-history/spec.md) |
| SCR-004 | カテゴリ管理 | [category-management/spec.md](category-management/spec.md) |
| SCR-005 | アーカイブ済みTODO一覧 | [archived-todos/spec.md](archived-todos/spec.md) |
| SCR-006 | 設定 | [settings/spec.md](settings/spec.md) |
| SCR-007 | カテゴリ別TODO一覧 | [category-todo-list/spec.md](category-todo-list/spec.md) |

独立した画面として追加する必要が生じた場合は、画面IDを付けてディレクトリと仕様ファイルを追加する。ダイアログ、ボトムシートなど画面内で完結するUIは、原則として呼び出し元の画面仕様に含める。

## 動作確認項目

全画面の受け入れ条件と画面横断要件は、[総合動作確認項目書](../test-specs/README.md)で一意のテストIDへ対応付ける。画面仕様を変更した場合は、対応する動作確認項目も同時に更新する。

## ファイル構成

各画面は次の構成で管理する。

```text
screen-specs/
└── <screen-name>/
    ├── spec.md
    └── assets/        # 必要になった場合のみ作成
```

`assets` にはワイヤーフレームや画面固有の参考画像を配置する。空のディレクトリは作成しない。

## 仕様に含める項目

各画面の `spec.md` には、原則として次を記載する。

1. 目的
2. 表示経路と終了経路
3. レイアウトと構成要素
4. 表示する情報と表示条件
5. ユーザー操作
6. 画面遷移
7. 状態別表示
8. 入力規則とバリデーション
9. 確認、エラー、フィードバック
10. アクセシビリティ
11. 受け入れ条件
12. 未決定事項

## 文書状態

各仕様には次のいずれかの状態を記載する。

- 未策定: 画面の役割だけが決まっており、詳細検討前
- 策定中: 詳細を検討中
- 確定: 実装可能な粒度で合意済み
- 改訂中: 確定後の変更を検討中
