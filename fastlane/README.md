# Google Play掲載成果物

Google Play Consoleへ登録する日本語の掲載文と画像を、Fastlane Supply互換のディレクトリ構成で管理します。Fastlaneによる自動公開はまだ設定しておらず、このディレクトリを追加しただけでGoogle Playへ送信されることはありません。

## 掲載文

次のファイルは .agents/non-functional-specs/release-specs/store-listing-copy-and-assets.md を正本として同期します。

- metadata/android/ja-JP/title.txt
- metadata/android/ja-JP/short_description.txt
- metadata/android/ja-JP/full_description.txt
- metadata/android/ja-JP/changelogs/1.txt

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

投入されるデータは架空のTODO・カテゴリ・履歴です。撮影後は画像をこのリポジトリへ保存する前に、端末の時刻、通知、他アプリの情報などが写り込んでいないことを目視確認します。

## 公開候補の検証

すべての画像を配置した後、次のコマンドを実行します。

    node fastlane/verify-play-store.mjs --release

公開用検証は、画像が1枚でも不足する場合、寸法やPNG形式が異なる場合、アイコンが1,024KBを超える場合、フィーチャーグラフィックにアルファチャンネルがある場合に失敗します。

画像撮影時は通知や実在情報を含めず、Googleのテスト広告を使用します。ライトテーマを基本とし、少なくとも1枚はダークテーマで撮影します。撮影後は各画像を目視し、ぼかし、引き伸ばし、欠け、重複、デバッグ表示および第三者商標がないことも確認してください。
