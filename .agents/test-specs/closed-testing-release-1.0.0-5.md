# MATA 1.0.0 (5) Closed testing登録・更新確認手順

- 対象: `com.mochisofts.mata` / `1.0.0 (5)`
- 状態: Upload Key署名済み候補生成済み／Closed testingへのアップロード待ち
- 作成日: 2026-09-13
- 公開候補: [MATA 1.0.0 (5) 公開候補生成結果](release-candidate-1.0.0-5.md)
- 実施結果: [初回Closed testing実施台帳](closed-testing-log.md)

## 1. 登録する候補

| 項目 | 値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `5` |
| ソースcommit | `99281d24e22256632cc33c95624b3ca39691a106` |
| AAB | `app/release/1.0.0-5/mata-1.0.0-5.aab` |
| 容量 | 12,673,367 bytes |
| SHA-256 | `888272618220628cefdef448f4f96bc28d01f7f22eea6b6b64757a34e64e2ad9` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| リリース名 | `1.0.0 (5) Closed testing` |
| 対象トラック | Closed testing |

`app/release/`はGit除外対象である。成果物一式をアクセス制限された永続保管先にも複製する。

## 2. リリースノート

Play Consoleへ次を登録する。内容は`fastlane/metadata/android/ja-JP/changelogs/5.txt`と一致させる。

> 主要画面をカードベースのデザインに統一し、余白や配色を見やすく改善しました。
> カレンダー履歴とアーカイブ済みTODOの詳細画面を、情報を確認しやすい構成に改善しました。

## 3. Play Consoleへの登録

1. `テストとリリース > テスト > クローズド テスト`から現在使用中のトラックを開く。
2. `新しいリリースを作成`し、第1節のAABだけをアップロードする。
3. 解析後、Application ID、versionName `1.0.0`、versionCode `5`を確認する。
4. リリース名と第2節の日本語リリースノートを入力する。
5. 既存の対象国・地域、テスター設定およびフィードバック先が維持されていることを確認する。
6. エラーが0件であることを確認する。Native Debug Symbols未登録の既知警告だけなら文面を記録し、空または架空のシンボルを登録しない。
7. 内容を確認し、Closed testingとして公開する。Productionへは公開しない。

## 4. Console公開後の確認

- [ ] ProductionではなくClosed testingへ公開した。
- [ ] versionName `1.0.0`、versionCode `5`だけが新しいリリースへ含まれる。
- [ ] 公開状態と公開日時を記録した。
- [ ] 対象国・地域、テスター設定およびフィードバック先が維持されている。
- [ ] 新規のエラーまたは警告がない。既知警告は内容を記録した。
- [ ] Pre-launch report、SDK Indexおよびポリシー状態を記録した。

## 5. versionCode 5への上書き更新

現在Google Playから配布されているClosed testing版を持つ端末を1台以上使用し、アンインストールせずGoogle Playから更新する。

### 5.1 更新前

1. `adb shell dumpsys package com.mochisofts.mata`またはアプリ情報で更新前のversionCodeを記録する。
2. TODO、カテゴリ、完了・スキップ履歴、設定、通知およびウィジェットが存在することを確認する。
3. 既存の基準バックアップを上書きせず、必要ならversionCode 5更新用バックアップを別名で作成する。

### 5.2 更新後

- [ ] versionCodeが`5`である。
- [ ] アプリが正常起動し、不必要な初期化や再同意を要求しない。
- [ ] TODO、カテゴリ、履歴、設定、通知およびウィジェットが維持される。
- [ ] 主要画面をカードベースのレイアウトで表示する。
- [ ] 画面左右の余白、カード間隔、背景色およびカード色が画面間で統一されている。
- [ ] TODO編集画面の入力欄が角丸で、区分ごとのカード内に表示される。
- [ ] カレンダー履歴とアーカイブ済みTODOの詳細を全画面で表示し、情報の区切りを判別できる。
- [ ] ライト・ダークテーマで背景、カード、文字および状態を判読できる。
- [ ] TODOの完了・スキップ、履歴表示、通知、ウィジェットおよびバックアップの主要操作を継続できる。
- [ ] クラッシュ、ANRまたはデータ欠損がない。

## 6. 完了条件

- [x] Upload Key署名済みversionCode 5 AABを生成し、公開候補検査に合格した。
- [ ] versionCode 5をClosed testingへ公開した。
- [ ] Console上の公開結果と警告を台帳へ転記した。
- [ ] Google Play経由のversionCode 5への上書き更新が成功した。
- [ ] 更新後のデータ、設定、通知およびウィジェットが維持された。
- [ ] versionCode 5のカードレイアウトと詳細画面を実機で確認した。

テスターの氏名、メールアドレス、Googleアカウント、Googleグループ情報およびオプトインURLは公開リポジトリへ記録しない。
