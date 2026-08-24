# FTP Synchronization

[中文](../README.md) | [English](README.en.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

FTP Synchronization は、ローカルディレクトリと FTP ディレクトリの間でファイルを定期的にアップロード／ダウンロードする JavaFX デスクトップアプリです。時刻ベースの動的パスに対応し、データ、画像、メッセージ、機器ファイルの同期に利用できます。

## 主な機能

- 複数のアップロード／ダウンロードルールを追加、表示、編集、削除、有効化できます。
- 年、月、日、時、分、秒のプレースホルダーと分単位の時刻オフセット。
- 転送先に存在しないファイルだけを同期します。
- ルール単位または全体の最新ファイル数、安定時間、FTP タイムアウト、接続プール。
- FTP 接続テスト、システムログ、ファイルごとの転送ログ。
- システム言語、中国語、英語、日本語、韓国語を即時切替。
- Windows、Linux、macOS の x64/ARM64 パッケージを自動生成。

## ダウンロードと実行

GitHub Actions Artifacts または Releases から OS と CPU に合うパッケージを取得してください。

| OS | x64 | ARM64 |
|---|---|---|
| Windows | `ftp-synchronization-<version>-windows-x64.zip` | `ftp-synchronization-<version>-windows-arm64.zip` |
| Linux | `ftp-synchronization-<version>-linux-x64.tar.gz` | `ftp-synchronization-<version>-linux-arm64.tar.gz` |
| macOS | `ftp-synchronization-<version>-macos-x64.tar.gz` | `ftp-synchronization-<version>-macos-arm64.tar.gz` |

Java を別途インストールする必要はありません。各パッケージの `jre/` に JavaFX を含む Liberica Java 21 JRE が入っています。展開後に `run.bat`、`run.sh`、または `run.command` を実行します。

`ftp-synchronization.jar` にはアプリと JavaFX 以外の依存関係だけが含まれます。JavaFX のクラス、モジュール、ネイティブライブラリは JAR に入りません。

## クイックスタート

1. 「FTP・実行設定」を開きます。
2. FTP ホスト、ポート、ユーザー名、パスワードを入力して接続をテストします。
3. 設定を保存します。
4. アップロードまたはダウンロードルールを追加します。
5. ディレクトリプレビューを確認して保存します。
6. 「今すぐ実行」で初回動作を確認します。

アップロードは最新 N 個の安定したローカルファイル、ダウンロードは最新 N 個の FTP ファイルを比較し、転送先に同名ファイルがある場合はスキップします。

## ルールとプレースホルダー

ルールには名前、方向、ローカルルート、リモートルート、動的パス、分オフセット、任意の最新ファイル数を設定します。

| 時刻 | ゼロなし | ゼロ埋め |
|---|---|---|
| 年 | `{year}` | `{YEAR}` |
| 月 | `{month}` | `{MONTH}` |
| 日 | `{day}` | `{DAY}` |
| 時 | `{hh}` | `{HH}` |
| 分 | `{mm}` | `{MM}` |
| 秒 | `{ss}` | `{SS}` |

`2026-08-01 03:05:09` の場合、`{year}/{MONTH}/{DAY}/{HH}/{MM}/{SS}` は `2026/08/01/03/05/09` になります。旧ルールの互換動作は維持されます。

## 言語と設定ファイル

設定画面で System、中文、English、日本語、한국어を選択できます。変更後、スケジューラーや FTP プールを停止せずに画面だけを再読込します。

- `pathRules.json`: 互換性を維持したルールファイル。
- `ftp-synchronization-settings.properties`: FTP、実行頻度、言語設定。
- `application.properties` / `application.yml`: 外部設定として引き続き利用可能。

新設定ファイルがない場合は旧 `ftp-upload-settings.properties` を読み込み、新しい名前へコピーします。旧ファイルは削除しません。

## ビルドとテスト

Java 21 で `./mvnw clean verify` を実行します。`target/ftp-synchronization.jar` が生成され、設定移行、四言語、FXML、JSON 互換性、テンプレート、リスナー、ProGuard、JAR 内 JavaFX 不在をテストします。CI はさらに 6 種類の配布パッケージを検証します。

ワークフローの詳細は [GitHub Actions パッケージガイド](GITHUB_ACTIONS_PACKAGING.zh-CN.md) を参照してください。

## セキュリティ

FTP パスワードは設定ファイルに平文で保存されます。アクセス権を制限し、スクリーンショット、ログ、コミット、Issue にパスワードを含めないでください。SHA-256 ファイルで配布物を確認できます。
