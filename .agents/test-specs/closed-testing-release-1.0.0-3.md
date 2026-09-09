# MATA 1.0.0 (3) Closed testing登録・更新確認手順

- 対象: `com.mochisofts.mata` / `1.0.0 (3)`
- 状態: Closed testing公開・versionCode 2から3への上書き更新確認済み／変更内容再試験中
- 作成日: 2026-09-09
- 公開候補: [MATA 1.0.0 (3) 公開候補生成結果](release-candidate-1.0.0-3.md)
- 実施結果: [初回Closed testing実施台帳](closed-testing-log.md)

## 1. 登録した候補

| 項目 | 値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `3` |
| ソースcommit | `307949e2068b1c56ff597c3730b05755b1f37e06` |
| AAB | `app/release/1.0.0-3/mata-1.0.0-3.aab` |
| 容量 | 12,600,782 bytes |
| SHA-256 | `75a79da8534841cda3312594a086369d1e7ff53029920b56904f856b16fbe019` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| リリース名 | `1.0.0 (3) Closed testing` |
| 対象トラック | Closed testing |

`app/release/`はGit除外対象である。成果物一式をアクセス制限された永続保管先にも複製する。

## 2. リリースノート

Play Consoleへ次のversionCode 3用リリースノートを登録する。

> 単発TODOに、実行日とは別の期限日を設定できるようになりました。
>
> 未完了TODOを完了またはスキップするまで翌日以降も表示できる、繰り越し設定を追加しました。
>
> 過去日のTODO詳細から現在のTODOを編集・完全削除できるようになり、入力欄でカテゴリ色を確認しやすくしました。

## 3. Play Console公開後の確認

次をConsoleの表示から確認し、[実施台帳](closed-testing-log.md)へ記録する。

- [x] ProductionではなくClosed testingへ公開した。
- [x] versionName `1.0.0`、versionCode `3`の候補を使用した。
- [x] リリース状態が「クローズドテスト公開開始」であり、2026年9月9日の公開であることを記録した。
- [ ] versionCode 3だけが新しいリリースへ含まれることを確認した。
- [ ] 対象国・地域、テスター設定およびフィードバック先が維持されている。
- [x] 新規の警告がないことを確認した。既知のNative Debug Symbolsに関する扱いは変更しない。
- [x] Pre-launch report、SDK Indexおよびポリシー状態を確認し、versionCode 2確認時から状況が変わっていないことを記録した。

Native Debug Symbols未登録の警告だけが表示された場合は、文面と日時を記録する。この候補はReleaseで`SYMBOL_TABLE`を有効化しているが、依存ライブラリで取得可能なシンボルがない。警告を消す目的で空または架空のZIPを登録しない。エラーへ変わった場合は公開を止める。

## 4. versionCode 2から3への上書き更新

既存のClosed testing版versionCode `2`を持つPixel 9aを使用する。versionCode `2`をアンインストールした端末は更新確認に使用しない。

### 4.1 更新前

1. アプリがversionCode `2`であることを確認する。
2. versionCode 2確認時に作成した基準バックアップ`B0`を上書きしない。
3. TODO、カテゴリ、完了・スキップ履歴、設定、通知およびウィジェットが存在することを確認する。
4. 更新前の状態を個人情報を含まない形で記録する。

### 4.2 更新

1. アプリをアンインストールせず、同じGoogleアカウントでClosed testingのストアページを開く。
2. Google Playの反映後に`更新`する。自動更新された場合は更新日時を記録する。
3. `adb shell dumpsys package com.mochisofts.mata`またはPlay Consoleの端末表示からversionCode `3`を確認する。

### 4.3 更新後

- [x] アプリが正常起動し、不必要な初期化や再同意を要求しない。
- [x] TODO、カテゴリ、色、アイコン、並び順および設定が維持される。
- [x] 完了・スキップ履歴と現在の繰り返し状態が維持され、重複発生しない。
- [x] 通知権限と通知設定が維持され、通知を再登録できる。
- [x] ウィジェットが最新内容を表示し、操作結果を反映する。
- [x] 手動バックアップを作成できる。
- [ ] 単発TODOへ実行日とは別の期限日を設定できる。
- [ ] 未完了繰り越しを有効にしたTODOが、完了またはスキップまで翌日以降も表示される。
- [ ] 過去日のTODO詳細から現在のTODOを編集・完全削除できる。
- [ ] TODO入力欄でカテゴリ色を確認できる。
- [x] クラッシュ、ANRまたはデータ欠損がない。

## 5. 保存する証跡

- Play Consoleのトラック、versionCode、状態、公開日時および警告。
- versionCode 2から3への更新前後、端末、Android/API、日時および結果。
- versionCode 3変更内容の再試験結果。
- テスターから得た自由記述フィードバックと、その対応判断。
- Pre-launch report、SDK Indexおよびポリシー状態。

テスターの氏名、メールアドレス、Googleアカウント、Googleグループ情報およびオプトインURLは公開リポジトリへ記録しない。

## 6. 完了条件

- [x] Upload Key署名済みversionCode 3 AABを生成し、公開候補検査に合格した。
- [x] versionCode 3をClosed testingへ公開したことをUSERが確認した。
- [x] Console上の公開結果と新規警告がないことを台帳へ転記した。
- [x] versionCode 2から3へのGoogle Play上書き更新が成功した。
- [x] 更新後のデータ、設定、通知およびウィジェットが維持された。
- [ ] versionCode 3の変更内容を再試験した。
- [x] 現時点の結果をClosed testing台帳と初回リリース進行記録へ反映した。

versionCode 3変更内容と不足環境の具体的な実施項目は[残実機・環境試験計画](release-v3-device-verification-plan.md)を使用する。
