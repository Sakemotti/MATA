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

## 画像の配置先

画像は実際のRelease相当アプリと提供済みブランド素材から作成し、次の場所へ配置します。スクリーンショットをモック画面や生成画像で代用しません。

- ストアアイコン: metadata/android/ja-JP/images/icon.png
- フィーチャーグラフィック: metadata/android/ja-JP/images/featureGraphic.png
- スマートフォン6枚: metadata/android/ja-JP/images/phoneScreenshots
- 7インチタブレット4枚: metadata/android/ja-JP/images/sevenInchScreenshots
- 10インチタブレット4枚: metadata/android/ja-JP/images/tenInchScreenshots

ファイル名、寸法、順序、代替テキストは play-store-manifest.json を正とします。ランチャー用の最大画像は432px、従来アイコンは192pxしかないため、512pxストアアイコンへ単純拡大しません。提供済みの高解像度元画像が必要です。

## フィーチャーグラフィックの生成

フィーチャーグラフィックは、アプリアイコンを大きく複製せず、MATAの循環・チェック表現とブランド色を使って、Java標準APIだけで1024×500の24-bit RGB PNGへ描画します。主要要素はGoogle Playでの表示切れを避けるため中央へ配置します。リポジトリルートで次を実行してください。

    java fastlane/tools/GenerateFeatureGraphic.java

生成先は`metadata/android/ja-JP/images/featureGraphic.png`です。生成後は`node fastlane/verify-play-store.mjs`で寸法、色形式およびManifestとの一致を確認し、目視確認も行います。ブランド色や構図を変更した場合は、生成処理と画像を同じコミットで更新してください。

## 公開候補の検証

ストアアイコンとすべてのスクリーンショットを配置した後、次のコマンドを実行します。

    node fastlane/verify-play-store.mjs --release

公開用検証は、画像が1枚でも不足する場合、寸法やPNG形式が異なる場合、アイコンが1,024KBを超える場合、フィーチャーグラフィックにアルファチャンネルがある場合に失敗します。

画像撮影時は通知や実在情報を含めず、Googleのテスト広告を使用します。ライトテーマを基本とし、少なくとも1枚はダークテーマで撮影します。撮影後は各画像を目視し、ぼかし、引き伸ばし、欠け、重複、デバッグ表示および第三者商標がないことも確認してください。
