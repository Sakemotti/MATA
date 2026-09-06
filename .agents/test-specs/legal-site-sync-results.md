# 法的サイト同期結果

- 対象試験: `REL-022`
- 初回確認日: 2026-09-07
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
