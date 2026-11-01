# ストア掲載画像更新記録（versionCode 11相当）

- 撮影・確認日: 2026-10-27
- 最終更新日: 2026-11-01
- 状態: 実画面14枚を取得・目視確認・配置済み／Play Console差し替え未実施
- 正本: [ストア掲載文・画像仕様](../non-functional-specs/release-specs/store-listing-copy-and-assets.md)
- 成果物: [Google Play掲載成果物](../../fastlane/README.md)

## 1. 対象と生成条件

本番UIの基準はmainの`4a9874d5138f16d1a3c56eaf05be64b18a0e30f1`。versionName `1.0.0`・versionCode `11`と同じ本番コードで、benchmark専用の架空履歴データだけを調整して撮影した。`app/src/main`、ビルド設定、SDK、権限、DB・バックアップ形式は変更しない。

- ビルド: `:app:assembleBenchmark`成功。Release相当の最適化・本番表示名`MATA`を使用し、debug署名・広告テストIDを設定する。
- 撮影APKのSHA-256: `afcf84cc62eba7729287ec59718bb82cfb1a12feb0ea9b6d836a0d4689b4cee3`
- Upload Keyの各入力を空にし、`MATA_REQUIRE_UPLOAD_SIGNING=false`で検証ビルドを実施。署名情報やAPKをGitへ含めない。
- 撮影端末: 専用AVD `mata_store_phone_api34`、Android 14 / API 34、Asia/Tokyo。利用者の実機データは変更しない。
- スマートフォン: 1,080×1,920px、360dpi、480×約853dp。
- 7インチ掲載欄向け: 1,920×1,080px、240dpi、1,280×720dp。
- 10インチ掲載欄向け: 1,920×1,080px、220dpi、約1,396×785dp。

上記dpはシステムバーを含む表示領域であり、アプリの有効領域はInsetsの分だけ小さい。タブレットの区分はPlay Consoleの掲載欄名で、実機の物理インチ数や特定タブレットの動作保証ではない。同じ撮影専用AVDの解像度・密度を切り替え、各表示領域でアプリを再レイアウトした。画像取得後のリサイズ・切り抜き・合成・文字追加や、スマートフォン画像の流用は行わない。

## 2. 架空データと画面状態

benchmark専用の`SEED_STORE_SCREENSHOT_DATA`を使用し、日常・家事・ゲームの3カテゴリ、実行中6件・アーカイブ済み1件を投入した。過去2か月の予定日に架空の完了履歴を作成し、前日のデイリーミッションはスキップ、直近の土曜日のキッチン整理は未完了として残す。週3回のTODOは撮影日から開始する。投入後、撮影日のゴミ出しをアプリ上で完了し、一覧を1/4完了・未完了3件にした。

すべて架空の情報であり、クローズドテストの利用実績ではない。カレンダーは10月26日を選択し、完了・スキップの履歴を表示した。繰り返し設定は「毎月 第X X曜日」の第1月曜・第3金曜を選択した既存TODOを開いた状態を撮影した。

## 3. 更新した画像

配置先はすべて`fastlane/metadata/android/ja-JP/images/`配下。順番と寸法は`fastlane/play-store-manifest.json`に従う。

| 掲載欄 | ファイル | 主な内容 | 目視結果 |
| --- | --- | --- | --- |
| スマートフォン | `phoneScreenshots/01-today.png` | 日付サマリ・未完了TODO入口・カテゴリカード | OK |
| スマートフォン | `phoneScreenshots/02-recurrence.png` | 毎月第X曜日の複数指定 | OK |
| スマートフォン | `phoneScreenshots/03-calendar-history.png` | 曜日色・日付選択・完了／スキップ履歴 | OK |
| スマートフォン | `phoneScreenshots/04-categories.png` | カテゴリ編集のカード・色・アイコン | OK |
| スマートフォン | `phoneScreenshots/05-widget.png` | ホーム画面に配置したカード形式の実ウィジェット | OK |
| スマートフォン | `phoneScreenshots/06-settings-backup.png` | ダークテーマ・手動バックアップ／復元 | OK |
| 7インチ | `sevenInchScreenshots/01-two-pane.png` | カテゴリ一覧と編集の2ペイン | OK |
| 7インチ | `sevenInchScreenshots/02-categories.png` | カテゴリ色・アイコン設定 | OK |
| 7インチ | `sevenInchScreenshots/03-calendar-history.png` | カレンダーと当日履歴の2ペイン | OK |
| 7インチ | `sevenInchScreenshots/04-archive.png` | アーカイブ一覧・集計・履歴・復元／削除 | OK |
| 10インチ | `tenInchScreenshots/01-two-pane.png` | カテゴリ一覧と編集の2ペイン | OK |
| 10インチ | `tenInchScreenshots/02-categories.png` | カテゴリアイコンの選択 | OK |
| 10インチ | `tenInchScreenshots/03-calendar-history.png` | カレンダーと当日履歴の2ペイン | OK |
| 10インチ | `tenInchScreenshots/04-archive.png` | アーカイブ一覧・集計・履歴・復元／削除 | OK |

## 4. 品質確認と範囲

- 14枚ともADBの`screencap -p`で取得したPNGをそのまま使用し、個別に目視確認した。
- 端末フレーム、加工、実在する個人情報、通知内容、`MATA Dev`等のデバッグ表示、本番広告は含めない。
- ステータスバーはOSのデモ表示で9:41・Wi-Fi・電池を整え、通知アイコンを非表示にした。アプリ内の履歴日時は架空データを投入した日時のままで、利用者の実績ではない。
- 広告はテストID設定・広告未表示のbenchmarkを使用した。製品版に広告がないことを示すものではなく、ストア説明の広告表示案内は変更しない。
- ウィジェット画像はOS標準ホームへ実際に配置した状態。固定プレビューではない。他アプリのショートカット・自動候補は外したが、OS標準検索バーは加工せず残した。
- 設定画面はダーク、他はライト。スクロール途中の画像は実際のスクロール位置をそのまま記録し、画面外まで続くカードを合成でつなげない。
- アイコン・フィーチャーグラフィックは既存のまま変更しない。
- `node fastlane/verify-play-store.mjs --release`は成功し、14枚と既存のアイコン・フィーチャーグラフィックの形式・寸法・配置、掲載文の同期を確認した。
- 法的文書、アーキテクチャ、外部データ契約、自動試験証跡の各検証も成功した。PRでは通常のAndroid CI・CodeQLを確認してからマージする。

この撮影は掲載画像の確認に限る。Play経由のversionCode 11の10→11更新回帰、広告の実配信、原報告者の改善再確認、Closed testingの要件達成、Production accessの承認を補完しない。配布済みAAB・ZIP・候補メタデータは再生成・変更しない。

## 5. Play Consoleへの反映（未実施）

2026-11-01のUSER回答でも、前回用意した最新画像への差し替えは未完了である。配布用ZIPは`build/store-screenshots-v11/MATA-store-screenshots-v11.zip`に保管し、SHA-256 `D5A93D523F1CD17F4A63010024BBEEAB6E2D478CC708A72B1B9446D6F3DE03D6`を再照合した。今回は初回Production前の最終確認として差し替え・保存まで進め、審査送信・公開は[外部最終確認票](production-console-final-check-1.0.0-11.md)と公開実行票の最終判断後に行う。

1. メインのストア掲載情報を開く。
2. スマートフォン6枚、7インチ4枚、10インチ4枚を、それぞれ上記画像へ差し替える。
3. 各欄をファイル名の番号順に並べ、プレビューで表示・順序を確認する。
4. アイコン・フィーチャーグラフィックは今回差し替えない。
5. 保存後、公開の概要で状態を確認・記録する。審査送信・公開は最終公開判断後とし、保存完了だけで送信済み・公開済みとは記録しない。

実際の差し替え・保存・審査送信・公開完了は、ユーザーまたはConsoleで確認できた結果だけを追記する。
