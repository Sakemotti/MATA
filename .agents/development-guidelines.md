# MATA 開発ガイドライン

- 文書状態: 初版
- 最終更新日: 2026-08-10

## 1. 技術基盤

- Kotlinを実装言語とする。
- Android Gradle Plugin 9系の内蔵Kotlinを使用する。
- UIはJetpack ComposeとMaterial Design 3で実装する。
- Activityは原則として単一構成とし、画面はComposeで構成する。
- 非同期処理にはKotlin Coroutinesを使用する。
- 日付と時刻には `java.time` APIを使用する。

起動経路、Navigation Compose、SplashScreen、初期化順序、障害回復および起動性能は[アプリ起動・初期化・復帰仕様](startup-specs/README.md)に従う。

現在のウィンドウサイズに基づくナビゲーション、2ペイン、Edge-to-edge、Insets、折りたたみ端末および入力方式は[画面サイズ・適応レイアウト仕様](adaptive-layout-specs/README.md)に従う。画面方向、アスペクト比およびActivityのリサイズを制限しない。

## 2. アプリ識別子

- Namespace: `com.mochisofts.mata`
- Release Application ID: `com.mochisofts.mata`
- Debug Application ID: `com.mochisofts.mata.debug`
- Debug表示名: `MATA Dev`
- Release表示名: `MATA`

Google Playへ公開するApplication IDは変更しない。公開後の変更は別アプリとして扱われるため、変更が必要な場合は実装前に確認する。

## 3. 対応SDK

- minSdk: 26
- targetSdk: 36
- compileSdk: 37.1

targetSdkはGoogle Playの公開要件とAndroidの挙動変更を確認したうえで更新する。compileSdkは利用するAndroidXライブラリが要求するバージョン以上とする。

## 4. パッケージ構成

機能の増加に合わせ、次の責務で分割する。

```text
com.mochisofts.mata
├── core/       # 日時、通知、共通UI、共通ユーティリティ
├── data/       # ローカルDB、設定、バックアップ、Repository実装
├── domain/     # モデル、繰り返し計算、Repositoryインターフェース
└── ui/         # 画面、ViewModel、ナビゲーション、テーマ
```

- UIからデータ保存処理を直接呼ばず、ViewModelとRepositoryを経由する。
- 繰り返し計算と論理日計算はAndroid UIから分離し、ローカル単体テストを可能にする。
- 画面固有コードが大きくなった場合は、`ui/<feature>` 単位で分割する。

## 5. 状態管理

- UI状態は不変データとして公開する。
- 画面からの操作はイベントとしてViewModelへ渡す。
- 永続化が必要な設定と、一時的な画面状態を区別する。
- 画面回転、プロセス再生成、アプリ再起動の各ケースを仕様に従って扱う。

## 6. データ保存

- TODO、カテゴリ、履歴は端末内データベースへ保存する。
- 小規模なユーザー設定は設定用ストレージへ保存する。
- クラウド同期とアカウント機能は導入しない。
- Androidの自動バックアップは無効とする。
- 手動バックアップはStorage Access Frameworkを使用し、アプリ外の任意の場所へユーザー操作で保存する。

具体的な保存モデルは[データモデル仕様](domain-specs/data-model.md)、手動バックアップ形式は[手動バックアップ仕様](backup-specs/README.md)に従う。

UI、Room、繰り返し計算、メモリ、バックグラウンド処理、電力および性能計測は[性能・省電力仕様](performance-specs/README.md)に従う。

## 7. コーディング規約

- Kotlin公式コーディング規約を基本とする。
- ファイルはUTF-8、改行はLF、インデントはスペース4個とする。
- UI文字列は原則として文字列リソースへ置く。
- 色、余白、文字スタイルはMaterialThemeまたは共通トークンから参照する。
- 色だけに依存した状態表現を避け、テキスト、アイコン、セマンティクスを併用する。
- 公開APIと複雑な業務ルールには、目的が分かるコメントまたはテストを付ける。

具体的なColorScheme、Typography、Shapes、寸法、共通コンポーネント、アイコン、モーションおよびアクセシビリティは[MATAデザインシステム仕様](design-system-specs/README.md)に従う。

## 8. テスト

- 実装完了とリリース前の確認には、[総合動作確認項目書](test-specs/README.md)を使用する。
- 起動経路と起動性能は、[起動の性能・試験仕様](startup-specs/performance-and-testing.md)も併用する。
- 画面サイズ境界、マルチウィンドウ、折りたたみ端末および入力方式は、[適応レイアウトの試験・公開判定仕様](adaptive-layout-specs/testing-and-release.md)も併用する。
- UI、データ、大量件数、メモリおよびバックグラウンド処理は、[性能計測・公開判定仕様](performance-specs/benchmark-and-release.md)も併用する。
- 各画面仕様の受け入れ条件は、画面別のテストIDと1対1で追跡する。
- 論理日、繰り返し、期限、履歴集計はローカル単体テストを必須とする。
- ViewModelは状態遷移を単体テストする。
- 主要なユーザーフローはCompose UIテストで確認する。
- データベースのマイグレーション追加時は移行テストを追加する。
- 不具合修正時は、可能な限り再現テストを先に追加する。
- 例外分類、再試行、縮退、競合および障害回復は[エラー処理・障害回復仕様](error-handling-specs/README.md)に従い、障害注入試験を追加する。

## 9. GitとCI

- `main` は常にビルド可能な状態を保つ。
- 機能開発は `feature/<topic>` ブランチで行う。
- 自動生成物、署名鍵、ローカルSDK設定、機密情報をコミットしない。
- GitHub Actionsで単体テスト、Lint、Debugビルドを実行する。
- DependabotでGradle依存関係とGitHub Actionsを週次確認する。

## 10. 完了条件

実装を完了とする前に、少なくとも次を満たす。

1. 対象画面仕様の受け入れ条件を満たしている。
2. `testDebugUnitTest` が成功する。
3. `lintDebug` が成功する。
4. `assembleDebug` が成功する。
5. 画面変更はライト・ダークテーマ、フォント拡大、各ウィンドウサイズクラスで確認する。
6. 新しい権限、外部SDK、データ収集がある場合は仕様とプライバシー文書を更新する。
