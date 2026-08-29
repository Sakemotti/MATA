# MATA 法的文書サイト

MATAの法的文書を mochisofts.com で公開するための、GitHub Pages向け静的サイト雛形です。現在は公開前原稿であり、このディレクトリをそのまま本番公開してはいけません。

## 公開予定URL

- https://mochisofts.com/
- https://mochisofts.com/mata/privacy
- https://mochisofts.com/mata/terms
- https://mochisofts.com/mata/commercial-transactions
- https://mochisofts.com/mata/external-transmission
- https://mochisofts.com/app-ads.txt

法的文書の正本は .agents/non-functional-specs/legal-specs 配下のMarkdownです。本文を変更するときはMarkdownを先に更新し、同じ変更をHTMLへ反映してください。

## ローカル確認

リポジトリルートから次のコマンドを実行し、http://127.0.0.1:8765/ を開きます。ルート相対リンクを使用しているため、HTMLファイルの直接表示ではなくHTTP経由で確認してください。

    node legal-site/serve.mjs

PowerShellによる構造検証は次のコマンドで実行します。

    powershell -ExecutionPolicy Bypass -File legal-site/verify.ps1

この検証は、公開前プレースホルダーと検索除外を許容します。内部リンク、必須HTML要素、原稿の主要項目、正本MarkdownのSHA-256との同期、JavaScriptおよび解析・広告タグの不在を確認します。正本を更新した場合は、HTMLへ同じ変更を反映してからHTML冒頭のsource-sha256を更新してください。

## 公開パッケージの生成

MATAリポジトリはPrivateのまま維持し、無料公開には別のPublicリポジトリ`Sakemotti/mochisofts.github.io`を使用します。原稿Markdown、テンプレート、READMEおよび検証スクリプトを誤って公開しないよう、このディレクトリ全体をコピーしてはいけません。

すべての公開前ブロッカーを解消した後、空の出力ディレクトリを指定して次を実行します。相対パスはリポジトリルートを基準とします。

    powershell -ExecutionPolicy Bypass -File legal-site/package-release.ps1 -OutputDirectory ../mata-legal-site-release

この処理は最初に`verify.ps1 -ForRelease`を実行し、失敗した場合はパッケージを作成しません。成功時はHTML、CSS、`CNAME`、`app-ads.txt`、`.nojekyll`等の許可対象だけを出力し、各ファイルのSHA-256を`SHA256SUMS`へ記録します。既存ファイルの誤削除を避けるため、出力先が空でない場合は失敗します。

生成物を目視確認してからPublicリポジトリの`main`ブランチ直下へ反映します。初期運用ではMATAリポジトリからPersonal Access Tokenで自動pushせず、公開差分をPublicリポジトリ側でレビューしてcommitします。

PublicリポジトリのSettings → Pagesでは`Deploy from a branch`、`main`、`/(root)`を選択します。カスタムドメインをDNSより先にGitHubへ登録し、GitHubアカウント側でドメイン所有を検証してからDNSを設定します。DNS反映後は`Enforce HTTPS`を有効にし、TXT検証レコードを削除しません。ワイルドカードDNSは使用しません。

## 公開前の必須作業

1. 法的文書仕様の公開ブロッカーをすべて解消する。
2. 公開対象HTMLから未確定値、公開前表示、draft-notice要素およびnoindex指定を削除する。404.htmlのnoindex指定は維持する。
3. robots.txtを本番方針へ変更し、サイト全体のDisallowを解除する。
4. CNAME.templateをCNAMEへ変更し、値がmochisofts.comであることを確認する。
5. AdMobが発行する正式な内容でapp-ads.txtを作成する。templateの文言は使用しない。
6. `package-release.ps1`で公開許可対象だけを空のディレクトリへ生成し、`SHA256SUMS`と内容を確認する。
7. Publicの`Sakemotti/mochisofts.github.io`リポジトリへ生成物だけを配置し、GitHub Pages、カスタムドメイン、DNS、HTTPSを設定する。
8. 次の公開用検証が成功することを確認する。

       powershell -ExecutionPolicy Bypass -File legal-site/verify.ps1 -ForRelease

9. PC幅、スマートフォン幅、200%文字拡大、キーボード操作、ダークモードおよび印刷表示を確認する。
10. 公開URLが認証、Cookie同意、JavaScriptなしで全文を返すことを確認する。
11. Google Play Console、アプリ内リンク、Data safety、UMP、価格およびapp-ads.txtと公開内容を照合する。

公開用検証は、プレースホルダー、公開前表示、検索除外、CNAME未確定またはapp-ads.txt未確定が残っている場合に失敗します。
