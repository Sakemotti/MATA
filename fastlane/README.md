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

## スクリーンショット撮影

撮影キー、出力先、寸法および代替テキストは`play-store-manifest.json`で一元管理します。利用可能な撮影キーは次のコマンドで確認できます。この操作は端末へ接続しません。

    powershell -ExecutionPolicy Bypass -File fastlane/capture-store-screenshot.ps1

撮影には、他のデータを保存していない専用のDebug版端末またはエミュレーターを使用します。次の`-Seed`操作は、選択した端末の`com.mochisofts.mata.debug`が保持するTODO、履歴等を削除し、日付に追従する架空データへ置き換えます。Release版`com.mochisofts.mata`は対象にできません。複数端末を接続している場合は、すべてのコマンドへ`-DeviceSerial <serial>`を指定します。

    powershell -ExecutionPolicy Bypass -File fastlane/capture-store-screenshot.ps1 -Seed
    powershell -ExecutionPolicy Bypass -File fastlane/capture-store-screenshot.ps1 -Prepare

`-Prepare`はSystem UIのデモモードで時刻を10:00、電池を100%にし、通知アイコンを隠します。画面サイズ、密度、向き、テーマおよびアプリ画面は変更しません。端末がデモモードに対応しない場合は失敗として扱い、通知や実在情報が残っていないことを手動で確認します。

Manifestの順序に従ってアプリを操作し、表示中の画面を撮影キーで保存します。既存画像は既定で上書きせず、意図して差し替える場合だけ`-Force`を付けます。撮影画像が指定寸法の8-bit RGB/RGBA PNGでなければ、成果物へ移動せず失敗します。

    powershell -ExecutionPolicy Bypass -File fastlane/capture-store-screenshot.ps1 -CaptureKey phone-01-today

ウィジェット画像は、架空データ投入後にホーム画面へMATAウィジェットを追加してから撮影します。設定画像またはカテゴリ画像のうち少なくとも1枚は、アプリ設定でダークテーマへ切り替えて撮影します。全撮影後はデモモードを解除し、掲載成果物全体を検査します。

    powershell -ExecutionPolicy Bypass -File fastlane/capture-store-screenshot.ps1 -Finish
    node fastlane/verify-play-store.mjs --release

撮影用データ投入は`StoreScreenshotDataSeedTest`だけが実行し、Debug application IDの検証後にデータを置き換えます。撮影後も画像のぼかし、欠け、第三者商標、実在情報およびデバッグ表示を目視確認してください。

## 公開候補の検証

ストアアイコンとすべてのスクリーンショットを配置した後、次のコマンドを実行します。

    node fastlane/verify-play-store.mjs --release

公開用検証は、画像が1枚でも不足する場合、寸法やPNG形式が異なる場合、アイコンが1,024KBを超える場合、フィーチャーグラフィックにアルファチャンネルがある場合に失敗します。

画像撮影時は通知や実在情報を含めず、Googleのテスト広告を使用します。ライトテーマを基本とし、少なくとも1枚はダークテーマで撮影します。撮影後は各画像を目視し、ぼかし、引き伸ばし、欠け、重複、デバッグ表示および第三者商標がないことも確認してください。
