# ストアアイコン原本

`background.png`と`foreground.png`は、提供済みのMATAランチャーアイコン原本から書き出された1,024×1,024pxレイヤーである。

- `background.png`: 不透明なフルブリード背景
- `foreground.png`: 透過付きのブランド前景

Google Play用成果物は、角丸や外周シャドウを加えず、`fastlane/tools/GenerateStoreIcon.java`で両レイヤーを512×512pxへ合成する。ランチャー用の密度別画像を生成元として使用しない。
