# 法的サイト同期結果

- 対象試験: `REL-022`
- 初回確認日: 2026-09-07
- 最終再確認日: 2026-09-17
- 正本リポジトリ: `Sakemotti/MATA`
- 公開リポジトリ: `Sakemotti/matadoc`
- 公開URL: `https://mochisofts.com/`

## 初回リリースの同期記録

| 項目 | 記録 |
| --- | --- |
| 正本側commit | [`45ead6a73ac742899cb812e93fbbb0c01f1cf68f`](https://github.com/Sakemotti/MATA/commit/45ead6a73ac742899cb812e93fbbb0c01f1cf68f) |
| 公開側commit | [`ad26db4aed4f97eaed7d74d66bcc44d63d90fda2`](https://github.com/Sakemotti/matadoc/commit/ad26db4aed4f97eaed7d74d66bcc44d63d90fda2) |
| Pages deployment | [run 33757973477](https://github.com/Sakemotti/matadoc/actions/runs/33757973477)、`success` |
| デプロイ完了 | 2026-09-03 21:55:44 JST |
| 再確認日時 | 2026-09-07 06:50:43 JST |
| 実施者 | RELEASE_OWNER。アプリ内遷移はUSER確認済み |

正本側の最後の`legal-site`変更は`45ead6a`である。公開側`ad26db4`はその後に作成され、GitHub Pagesは公開側の同commitを`https://mochisofts.com/`へデプロイしている。

## 内容比較

- 正本`legal-site`の13ファイルについて`git hash-object`でGit blob SHAを算出した。
- 公開リポジトリmainの全blobをGitHub APIで取得し、同じ相対pathのSHAと比較した。
- 13/13ファイルでpathとblob SHAが一致した。
- 公開側だけにある`.gitignore`は配信本文ではない運用ファイルであり、許容する。
- 公開側だけに存在するHTML、CSS、JavaScript、サイトマップ、robots、CNAMEまたは`app-ads.txt`はない。

## 公開確認

Android Mobile相当のUser-Agentを使用し、次の4公開物がHTTP 200かつ正本と完全一致することを確認した。

| URL | HTTP | 正本一致 |
| --- | ---: | --- |
| `https://mochisofts.com/mata/privacy` | 200 | 一致 |
| `https://mochisofts.com/mata/terms` | 200 | 一致 |
| `https://mochisofts.com/mata/external-transmission` | 200 | 一致 |
| `https://mochisofts.com/app-ads.txt` | 200 | 一致 |

GitHub Pages設定はmainブランチのリポジトリルートを公開元とし、カスタムドメイン`mochisofts.com`、HTTPS強制、状態`built`である。

アプリ設定画面からプライバシーポリシーと利用規約を開けることはユーザーが実機で確認済みであり、`REL-015`にも記録されている。ReleaseのURLガードは同じ2つの公開URL以外を拒否する。

## 次回以降

法的サイトを変更するたびに新しい節を追加し、正本側commit、公開側commit、Pages deployment、確認日時、対象URLおよび実施者を記録する。過去の同期記録は上書きしない。

## 2026年9月17日 本番公開前再確認

### 検証対象

| 項目 | 記録 |
| --- | --- |
| 検証開始時の正本main | `241f9f3b4473453173dd039f5fa39f84b2747cad` |
| 正本側の最終`legal-site`変更 | `45ead6a73ac742899cb812e93fbbb0c01f1cf68f` |
| 公開側main | `ad26db4aed4f97eaed7d74d66bcc44d63d90fda2` |
| 公開側Pages deployment | run `33757973477`、`success` |
| 検証日 | 2026-09-17 JST |
| 結果 | 合格。再同期不要 |

正本13ファイルと公開側mainをGit blob SHAで再比較し、13/13ファイルが一致した。公開側だけにある`.gitignore`は配信内容へ影響しない運用ファイルであり、引き続き許容する。公開側だけに存在するHTML、CSS、JavaScript、CNAME、サイトマップ、robotsまたは`app-ads.txt`はない。

`node legal-site/verify.mjs --release`は成功し、必須4ページ、sitemapの4 URL、内部リンク、`lang="ja"`、viewport、Content Security Policy、単一`h1`、連絡先、CNAME、robotsおよび`app-ads.txt`を検証した。

### 公開ファイルの再取得

Android 17 / Pixel 9a相当のMobile User-AgentとWindows Desktop相当のUser-Agentで公開物を取得した。両User-Agentで全9件がHTTP 200となり、各レスポンスは正本ファイルとSHA-256が一致した。次表はMobile User-Agentの結果であり、Desktopも同じ容量・SHA-256・一致結果だった。

| URL | Content-Type | bytes | SHA-256 | 正本一致 |
| --- | --- | ---: | --- | --- |
| `https://mochisofts.com/` | `text/html` | 1,944 | `d4b7800f29c779e0ea20626d3c2afddc56c2b56b3f6bb14fea6f449c28db34ef` | 一致 |
| `https://mochisofts.com/mata/privacy` | `text/html` | 9,726 | `2f0b607616efb636119acab93647a2e24910e9e4b0a61044b500e5f9bda79940` | 一致 |
| `https://mochisofts.com/mata/terms` | `text/html` | 8,436 | `2bf447f3e8e6b350c5e12d26bd046f78fb8ed9400a18a5e7c47b7fa5580421ed` | 一致 |
| `https://mochisofts.com/mata/external-transmission` | `text/html` | 6,696 | `9d4c3d39c3ad467a99bae4b1b0a9cc78b08d2ad3fc191751b1d304ae53844568` | 一致 |
| `https://mochisofts.com/mata/commercial-transactions` | `text/html` | 2,017 | `36db8b7fbe84669e136c4f809165c7a9f71c2e656de3bb0f5bc52728a29375d6` | 一致 |
| `https://mochisofts.com/app-ads.txt` | `text/plain` | 59 | `1efe640c74a5cbb929e70cc529486dd51e837d941d3f9d963c9682338aa4113d` | 一致 |
| `https://mochisofts.com/assets/style.css` | `text/css` | 4,245 | `4f8139c301d1640909fe4a844abafde1f5aeffd638e0afc859cf663b8fe3be53` | 一致 |
| `https://mochisofts.com/robots.txt` | `text/plain` | 68 | `06008d188194fc872e6e40f1712e8dab481342d181bd797f0bd66a26295d5953` | 一致 |
| `https://mochisofts.com/sitemap.xml` | `application/xml` | 382 | `ff77098176ec6bbf27ff6b63ce6bcd6bba80589d2bd32af72f6e133a70fd86fc` | 一致 |

拡張子のない各文書URLは同一ホストの末尾スラッシュ付きURLへ解決し、本文は正本と一致した。次のHTTP URLはすべて301で同一パスのHTTPSへ転送された。

- `http://mochisofts.com/`
- `http://mochisofts.com/mata/privacy`
- `http://mochisofts.com/app-ads.txt`

### GitHub Pages・モバイル対応

GitHub Pages APIで次を確認した。

- 状態`built`、公開リポジトリ`public`
- 公開元はmainブランチのリポジトリルート
- カスタムドメイン`mochisofts.com`は`verified`
- HTTPS強制が有効
- HTTPS証明書は`approved`、対象ドメインは`mochisofts.com`、有効期限は2026年12月1日

公開HTMLは正本と完全一致しているため、全ページのviewport設定、JavaScript不要の静的本文、640px以下のモバイル用1列レイアウト、長いリンクの折り返し、表の横スクロールおよびライト・ダーク配色も正本どおり配信されている。今回の再確認では新しい実画面スクリーンショットを取得していないが、2026年9月7日の実機遷移確認時から公開HTML・CSSは変化しておらず、Mobile User-Agentでも同一本文とCSSを取得できた。

### `app-ads.txt`

公開内容は次の正式な1行だけで、正本と一致した。

```text
google.com, pub-6387608801909086, DIRECT, f08c47fec0942fa0
```

Publisher IDはアプリのAdMob設定と一致しており、プレースホルダー、余分な販売者、コメントまたはHTMLは含まれない。

### 判定

`REL-022`の本番公開前URL・同期ゲートは合格とする。法的正本または`app-ads.txt`を今後変更した場合は、公開リポジトリへの再同期、Pages deployment成功、13ファイルのblob比較、公開URLのSHA-256比較およびアプリ内遷移を再実施する。
