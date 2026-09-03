# MATA 法的文書サイト

`https://mochisofts.com/`で公開する静的サイトの原稿です。

## 公開URL

- トップ: `https://mochisofts.com/`
- プライバシーポリシー: `https://mochisofts.com/mata/privacy`
- 利用規約: `https://mochisofts.com/mata/terms`
- 外部送信に関する公表: `https://mochisofts.com/mata/external-transmission`
- app-ads.txt: `https://mochisofts.com/app-ads.txt`

## 公開構成と更新フロー

MATA本体は非公開リポジトリであり、この`legal-site`フォルダを法的サイトの正本とします。公開先は、GitHub Pages、`mochisofts.com`、HTTPSを設定済みの別の公開リポジトリです。

1. 本リポジトリで仕様と`legal-site`を同じブランチ上で変更し、ローカル検証とPull RequestのCIを成功させる。
2. 変更を本リポジトリの`main`へマージし、正本側のcommitを記録する。
3. ユーザーが`legal-site`の中身を公開リポジトリのルートへコピーし、公開リポジトリへcommit・pushする。公開リポジトリだけを先行編集しない。
4. GitHub Pagesのデプロイ完了後、変更した公開URLを認証なしで確認する。
5. アプリの設定画面からプライバシーポリシーと利用規約を開き、同じ最新版が表示されることを確認する。
6. 正本側commit、公開側commit、公開確認日時をリリース記録へ残す。

公開リポジトリへのコピーとpushは自動化せず、ユーザーが内容を確認して実施します。手順と証跡の詳細は本リポジトリの`.agents/non-functional-specs/legal-specs/publishing-workflow.md`に従います。

DNS設定値は変更される可能性があるため、このリポジトリへ固定値を記録せず、公開時点の
[GitHub公式カスタムドメイン手順](https://docs.github.com/ja/pages/configuring-a-custom-domain-for-your-github-pages-site/about-custom-domains-and-github-pages)
を使用します。

## app-ads.txt

`app-ads.txt`には、AdMobで確認済みのMATAの正式なパブリッシャーIDを設定しています。
`app-ads.txt.example`は新しいパブリッシャーIDへ移行する場合のテンプレートであり、公開用ではありません。
公開リポジトリへは`app-ads.txt`をコピーし、置換前のテンプレートを`app-ads.txt`として公開しないでください。

Google Playの一般公開後にAdMobとストア掲載情報をリンクし、app-ads.txtステータスが承認済みになることを確認します。

## ローカル検証

リポジトリのルートで次を実行し、必須ページ、内部リンク、問い合わせ先、サイトマップおよび公開前プレースホルダーを検査します。

```shell
node legal-site/verify.mjs
```

この検査はGitHub Actionsのリポジトリ検査でも実行します。別リポジトリへのコピー、公開後のHTTPSおよび実際のモバイル表示は別途確認します。

正式な`app-ads.txt`を配置した公開候補では、次のコマンドでCNAME、検索許可および公開必須ファイルも検査します。

```shell
node legal-site/verify.mjs --release
```

## 公開前確認

- 問い合わせ先が`com.mochisofts@gmail.com`で統一されている。
- 制定日・最終改定日が実際の公開日と一致している。
- MATAが有料商品またはサービスを販売しない構成であることと記載が一致している。
- Google Play、AdMob、UMPの実装内容と記載が一致している。
- 外部リンクがHTTPSで開ける。
- ページがスマートフォン幅で横スクロールせず表示できる。
- `app-ads.txt`に正式なAdMobパブリッシャーIDが設定されている。
- 公開リポジトリに秘密情報、広告ユニットID、署名情報を含めていない。
