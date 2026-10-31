# Google Play掲載成果物

Google Play Consoleへ登録する日本語の掲載文と画像を、Fastlane Supply互換のディレクトリ構成で管理します。Fastlaneによる自動公開はまだ設定しておらず、このディレクトリを追加しただけでGoogle Playへ送信されることはありません。

## 掲載文

次のファイルは .agents/non-functional-specs/release-specs/store-listing-copy-and-assets.md を正本として同期します。

- metadata/android/ja-JP/title.txt
- metadata/android/ja-JP/short_description.txt
- metadata/android/ja-JP/full_description.txt
- metadata/android/ja-JP/changelogs/<versionCode>.txt

文字数と正本との一致は次のコマンドで確認します。

    node fastlane/verify-play-store.mjs

同じドラフト検証をPull RequestのCIでも実行します。画像が未配置の間は不足を通知しますが、掲載文、マニフェスト、既に配置された画像に不整合があれば失敗します。

## 画像の配置先

画像は実際のRelease相当アプリと提供済みブランド素材から作成し、次の場所へ配置します。スクリーンショットをモック画面や生成画像で代用しません。

- ストアアイコン: metadata/android/ja-JP/images/icon.png
- フィーチャーグラフィック: metadata/android/ja-JP/images/featureGraphic.png
- スマートフォン6枚: metadata/android/ja-JP/images/phoneScreenshots
- 7インチタブレット4枚: metadata/android/ja-JP/images/sevenInchScreenshots
- 10インチタブレット4枚: metadata/android/ja-JP/images/tenInchScreenshots

ファイル名、寸法、順序、代替テキストは play-store-manifest.json を正とします。512pxストアアイコンは`icon.png`、1,024×500pxフィーチャーグラフィックは`featureGraphic.png`へ配置しています。スマートフォン6枚、7インチタブレット4枚、10インチタブレット4枚のスクリーンショットも配置済みです。ストアアイコンをランチャー用画像からの単純拡大で置き換えないでください。

## スクリーンショットの再取得

掲載用データはbenchmarkビルドだけに含まれるBroadcastReceiverから投入できます。既存データとの混在を避けるため、アプリを新規インストールしてから次の順序で実行します。

    ./gradlew :app:assembleBenchmark
    adb install -t app/build/outputs/apk/benchmark/app-benchmark.apk
    adb shell am broadcast -a com.mochisofts.mata.action.SEED_STORE_SCREENSHOT_DATA

投入されるデータは架空のTODO・カテゴリ・履歴です。過去2か月の予定日に完了履歴を用意し、前日のスキップと直近1週間の土曜日の未完了を残します。週3回のTODOは投入日から開始します。既存データの削除・初期化は、撮影専用エミュレーターと対象パッケージを確認した場合に限り、日常利用中の端末では行いません。

2026-10-27にversionCode 11と同じ本番UIから14枚を再取得しました。撮影条件と確認結果は[ストア画像更新記録](../.agents/test-specs/store-screenshot-refresh-v11.md)を参照してください。Android 14 / API 34の撮影専用エミュレーターで、スマートフォンは1,080×1,920px・360dpi、7インチ掲載欄用は1,920×1,080px・240dpi、10インチ掲載欄用は1,920×1,080px・220dpiで表示し直しています。タブレットの区分は掲載欄名であり、実機の物理インチ数を測定した記録ではありません。スマートフォン画像の拡大流用や、取得後のリサイズ・合成はしていません。

ウィジェットは標準ランチャーへ実際に配置したものを撮影し、配置前の固定プレビュー画像で代用しません。不要な他アプリのショートカットと自動候補は非表示にし、OS標準の検索バーはそのまま残します。撮影後は端末の通知、個人情報、デバッグ表示、本番広告が写り込んでいないことを目視確認します。

## 公開候補の検証

すべての画像を配置した後、次のコマンドを実行します。

    node fastlane/verify-play-store.mjs --release

公開用検証は、画像が1枚でも不足する場合、寸法やPNG形式が異なる場合、アイコンが1,024KBを超える場合、フィーチャーグラフィックにアルファチャンネルがある場合に失敗します。

画像撮影時は通知や実在情報を含めず、広告はGoogleのテストIDを設定した検証ビルド、または広告未表示のRelease相当レイアウトを使用します。今回のbenchmarkビルドはテストID設定・広告未表示の状態です。広告なしの製品であることを意味せず、掲載文の「広告を含む」は維持します。ライトテーマを基本とし、設定・バックアップ画像はダークテーマで撮影しています。各画像を目視し、ぼかし、引き伸ばし、意図しない欠け・重複、デバッグ表示および他社広告がないことも確認してください。ウィジェット画像に残るOS標準検索バーはMATAの機能・提携を表すものではありません。

リポジトリの画像更新だけではPlay Consoleは更新されません。Consoleのスマートフォン・7インチ・10インチ各掲載欄で旧画像を同名の新画像へ差し替え、順序とプレビューを確認して保存・審査送信してください。アイコンとフィーチャーグラフィックは今回変更していません。
