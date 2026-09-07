# MATA 1.0.0 (2) Closed testing登録・更新確認手順

- 対象: `com.mochisofts.mata` / `1.0.0 (2)`
- 状態: Play Console登録・Closed testing公開済み／上書き更新確認待ち
- 作成日: 2026-09-07
- 公開候補: [MATA 1.0.0 (2) 公開候補生成結果](release-candidate-1.0.0-2.md)
- 実施結果: [初回Closed testing実施台帳](closed-testing-log.md)

## 1. この手順の目的

署名済み公開候補を取り違えずにClosed testingへ登録し、Google Playで処理された版がローカルの候補と一致すること、およびInternal testingのversionCode `1`からデータを保ったままversionCode `2`へ更新できることを確認する。

この手順はConsole操作の引き渡し資料であり、実施済みの証跡ではない。登録結果は[初回Closed testing実施台帳](closed-testing-log.md)へ記録する。

## 2. 登録する唯一の候補

| 項目 | 値 |
| --- | --- |
| Application ID | `com.mochisofts.mata` |
| versionName / versionCode | `1.0.0` / `2` |
| ソースcommit | `1222267981f2a7887e8c2073bbd7c2bd1a18a78e` |
| AAB | `app/release/1.0.0-2/mata-1.0.0-2.aab` |
| 容量 | 12,538,422 bytes |
| SHA-256 | `a6f3a90728f14b1f45bb66dc8be141257aaac73c1d8a099caf7757d2e48137de` |
| Upload Key SHA-256 | `EC:63:FF:99:D4:80:DA:DD:2F:2E:21:42:0A:FD:E6:18:52:C3:57:38:4C:93:BA:AE:6E:03:DA:74:35:F2:93:4D` |
| リリース名 | `1.0.0 (2) Closed testing` |
| 対象国・地域 | 日本 |

アップロード直前に次を実行し、上表と一致しない場合は作業を中止する。

```powershell
Get-Item app\release\1.0.0-2\mata-1.0.0-2.aab | Select-Object Length
Get-FileHash -Algorithm SHA256 app\release\1.0.0-2\mata-1.0.0-2.aab
```

`app/release/`はGit除外対象であり、リポジトリのバックアップには含まれない。アップロード前に`1.0.0-2`ディレクトリ一式を、アクセス制限された永続保管先へ複製する。

## 3. テスターを設定する

Googleの[テスト設定の公式手順](https://support.google.com/googleplay/android-developer/answer/9845334?hl=ja)を正とし、Play Consoleの表示が本書と異なる場合は現行Consoleに従う。

1. MATAを選択し、`テストとリリース > テスト > クローズド テスト`を開く。
2. 初期のClosed testingトラックを管理するか、新しいトラックを作成する。実際のトラック名を台帳へ記録する。
3. `テスター`タブで、メールリストまたはGoogleグループのいずれか一方を設定する。
4. フィードバック先に`com.mochisofts@gmail.com`を設定する。
5. 対象国・地域が日本であることを確認する。
6. 設定を保存する。参加URLはリリースが公開状態になってから取得し、リポジトリ外の連絡経路だけで共有する。

テスターの氏名、Googleアカウント、メールアドレス、Googleグループの非公開情報および参加URLは、この公開リポジトリへ記録しない。台帳では`T01`〜`T12`の匿名IDを使う。

Internal testingへ参加中のアカウントは、アプリをアンインストールせずInternal testingから参加解除してからClosed testingへ参加する。Googleの公式案内では、Internal testingとOpen/Closed testingを同時にオプトインできない。

## 4. リリースを作成する

Googleの[リリース作成・公開の公式手順](https://support.google.com/googleplay/android-developer/answer/9859348?hl=ja)を正とする。

1. Closed testingの対象トラックで`新しいリリースを作成`を選択する。
2. Play App Signingが有効であることを確認する。アプリ署名鍵を変更しない。
3. 第2節のAABをアップロードする。成果物ライブラリのversionCode `1`を追加しない。
4. 解析完了後、パッケージ名、versionName `1.0.0`、versionCode `2`を確認する。
5. リリース名を`1.0.0 (2) Closed testing`とする。
6. 日本語のリリースノートへ、`fastlane/metadata/android/ja-JP/changelogs/2.txt`と同じ次の内容を入力する。

> MATAを公開しました。  
> 毎日・曜日・月末・第X曜日・一定間隔・週X回・月X回などの繰り返しTODO、カテゴリ、通知、ウィジェット、履歴、手動バックアップに対応しています。

7. 下書きを保存して次へ進み、エラーをすべて解消する。警告は内容と判断を記録してから進む。
8. `審査に送信`、`公開`または`Closed testingへの公開を開始`等、現在のConsoleが表示する操作で対象トラックへ送信する。Productionへは送信しない。
9. `テストとリリース > 最新のリリースとバンドル`または対象トラックで、審査・公開状態、更新日時、国・地域およびversionCode `2`を確認する。

Native Debug Symbolsの警告が表示された場合は、文面と日時を記録する。この候補はReleaseで`SYMBOL_TABLE`を有効にしているが、AAB内の依存ライブラリは配布元ですでにシンボル除去済みで、アップロード可能なシンボルがない。警告を消す目的で空または架空のZIPを登録しない。エラーへ変わった場合は公開を止める。

## 5. 公開後の同一性確認

次をすべて確認する。

- [x] 対象トラックがClosed testingであり、Productionではない。
- [x] 対象国・地域が日本である。
- [x] versionNameが`1.0.0`、versionCodeが`2`である。
- [x] Google PlayがAABを受理し、リリースにversionCode `2`だけが含まれる。
- [x] リリースノートが第4節と一致する。
- [x] エラーが0件で、警告ごとの進行判断を記録した。
- [x] テスター設定とフィードバック先が保存されている。
- [x] リリースが公開状態になった後、参加URLを取得できる。
- [x] Play Consoleが示す必要人数と継続日数を台帳へ転記した。

Upload Key証明書は、AABをアップロードした開発者をPlayが検証するための鍵である。端末へ配信されるAPKはPlayのアプリ署名鍵で署名されるため、端末側の証明書が第2節のUpload Keyと異なること自体は不具合ではない。両者を混同せず、`アプリの署名`画面ではUpload Key証明書とアプリ署名鍵証明書を別々に扱う。

## 6. versionCode 1から2への上書き更新

これはClosed testing参加者へ割り当てる試験項目ではなく、`OWNER`が行う正式確認である。既存Internal testing版を持つ端末を1台以上使う。versionCode `1`をアンインストールしてしまった端末は、この更新確認には使わない。

### 6.1 更新前

1. Internal testing版`1.0.0 (1)`がインストール済みであることを確認する。
2. テスト専用のカテゴリを作り、色、アイコン、並び順および一日の終了時刻を変更する。
3. 通常TODOと繰り返しTODOを作り、完了履歴とスキップ履歴を各1件以上作る。
4. 完了済み表示、週の開始曜日等の設定を既定値から変更する。
5. 将来時刻の通知を有効にし、ウィジェットをホーム画面へ配置する。
6. アプリの画面、通知設定およびウィジェットの更新前状態を、個人情報を含まない形で記録する。

### 6.2 更新

1. アプリをアンインストールせず、Internal testingから参加解除する。
2. Closed testingの参加URLを同じGoogleアカウントで開き、参加する。
3. Google Playの反映を待ち、ストアから`更新`する。テストリンクは初回公開や変更後、利用可能になるまで数時間かかる場合がある。
4. アプリ情報またはGoogle PlayでversionCode `2`へ更新されたことを確認する。

### 6.3 更新後

- [ ] アプリが起動し、初期化や再同意を不必要に要求しない。
- [ ] TODO、カテゴリ、色、アイコン、並び順およびカテゴリ別終了時刻が維持される。
- [ ] 完了・スキップ履歴と現在の繰り返し状態が維持され、重複発生しない。
- [ ] アプリ設定が維持される。
- [ ] 通知権限と通知設定が維持され、予定した通知が発火する。
- [ ] ウィジェットが読み込みエラーにならず、最新内容を表示して完了操作を反映する。
- [ ] 手動バックアップを作成できる。
- [ ] クラッシュ、ANRまたはデータ欠損がない。

結果は台帳の環境カバレッジ`Google Play経由の上書き更新`と、試験実施記録の新しい`CT-*`行へ記録する。不合格の場合はIssueを作成し、この候補の本番公開を保留する。修正版は同じversionCode `2`で置換できないため、versionCode `3`以上で新しい候補を作成する。

## 7. 保存する証跡

| 証跡 | 記録内容 | 保存上の注意 |
| --- | --- | --- |
| AAB照合結果 | ファイル名、容量、SHA-256、照合日時 | 台帳またはリリース保管先 |
| リリース概要 | トラック名、状態、versionCode、更新日時、国・地域 | テスター情報をマスクする |
| 警告・エラー | Consoleの文面、確認日時、判断 | 個人情報を含めない |
| テスター設定 | 管理方法、人数、保存確認 | メールアドレスや参加URLを記録しない |
| 上書き更新 | 端末、Android/API、更新前後version、日時、結果 | テスト専用データを使う |
| 公開後状態 | 参加URL取得可否、公開日時、Console上の参加要件 | URL自体は非公開経路で管理する |

スクリーンショットを保存する場合は、ファイル名を`CT-CONSOLE-v2-<内容>-<YYYYMMDD>.png`とする。メールアドレス、氏名、参加URL、端末の通知に含まれる実TODO、アカウント画像等が写る場合は、公開リポジトリへ追加しない。

## 8. 完了条件

- [x] 公開候補一式を永続保管先へ複製した。
- [x] 第2節のAABをClosed testingへ登録した。
- [x] Console上のversionCode、トラック、国・地域およびリリースノートが一致した。
- [x] エラーがなく、警告を記録した。
- [x] 参加URLを取得し、アクセス制限された連絡経路でテスターへ共有した。
- [ ] versionCode `1`から`2`へのGoogle Play上書き更新が成功した。
- [ ] 更新後のデータ、通知およびウィジェットが維持された。
- [ ] 結果を[初回Closed testing実施台帳](closed-testing-log.md)と[初回リリース進行記録](../non-functional-specs/release-specs/initial-release-status.md)へ反映した。
