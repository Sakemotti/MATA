# MATA 法的文書サイト

`https://mochisofts.com/`で公開する静的サイトの原稿です。

## 公開URL

- トップ: `https://mochisofts.com/`
- プライバシーポリシー: `https://mochisofts.com/mata/privacy`
- 利用規約: `https://mochisofts.com/mata/terms`
- 特定商取引法に基づく表記: `https://mochisofts.com/mata/commercial-transactions`
- 外部送信に関する公表: `https://mochisofts.com/mata/external-transmission`
- app-ads.txt: `https://mochisofts.com/app-ads.txt`

## 無料公開の構成

MATA本体は非公開リポジトリのため、このフォルダをそのままGitHub Pagesの無料公開元にはしません。
別途作成する公開リポジトリのルートへ、このフォルダの中身を配置してGitHub Pagesを有効化します。

1. 法的文書専用の公開リポジトリを作成する。
2. このフォルダの中身を公開リポジトリのルートへ配置する。
3. GitHub Pagesを`main`ブランチのルートから公開する。
4. GitHubの現行手順に従い、`mochisofts.com`のDNSへカスタムドメインを設定する。
5. GitHub Pagesで「Enforce HTTPS」を有効にする。
6. 上記6つの公開URLを、ログインなし・モバイル回線から確認する。

DNS設定値は変更される可能性があるため、このリポジトリへ固定値を記録せず、公開時点の
[GitHub公式カスタムドメイン手順](https://docs.github.com/ja/pages/configuring-a-custom-domain-for-your-github-pages-site/about-custom-domains-and-github-pages)
を使用します。

## app-ads.txt

`app-ads.txt.example`は公開用ではありません。AdMobでMATAのパブリッシャーIDを取得した後、
`pub-REPLACE_WITH_ADMOB_PUBLISHER_ID`を正式な値へ置換し、ファイル名を`app-ads.txt`に変更します。
置換前のテンプレートを`app-ads.txt`として公開しないでください。

公開後はAdMobのapp-ads.txtステータスが承認済みになることを確認します。

## ローカル検証

リポジトリのルートで次を実行し、必須ページ、内部リンク、問い合わせ先、サイトマップおよび公開前プレースホルダーを検査します。

```shell
node legal-site/verify.mjs
```

この検査はGitHub Actionsのリポジトリ検査でも実行します。公開後のHTTPS、DNSおよび実際のモバイル表示は別途確認します。

## 公開前確認

- 問い合わせ先が`com.mochisofts@gmail.com`で統一されている。
- 制定日・最終改定日が実際の公開日と一致している。
- 特定商取引法上の請求時開示方法を専門家が確認し、遅滞なく対応できる運用がある。
- Google Play、AdMob、UMP、Billingの実装内容と記載が一致している。
- 外部リンクがHTTPSで開ける。
- ページがスマートフォン幅で横スクロールせず表示できる。
- `app-ads.txt`に正式なAdMobパブリッシャーIDが設定されている。
- 公開リポジトリに秘密情報、広告ユニットID、署名情報を含めていない。
