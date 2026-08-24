# GitHub Actions 六平台打包说明

本文说明 `.github/workflows/build-release.yml` 的制作方式、发布边界和维护流程。

## 1. 目标产物

每次向任意分支 push 后生成：

```text
ftp-synchronization-<版本>-windows-x64.zip
ftp-synchronization-<版本>-windows-arm64.zip
ftp-synchronization-<版本>-linux-x64.tar.gz
ftp-synchronization-<版本>-linux-arm64.tar.gz
ftp-synchronization-<版本>-macos-x64.tar.gz
ftp-synchronization-<版本>-macos-arm64.tar.gz
```

每个文件都有同名 `.sha256`。普通 push 的 Actions Artifacts 保留 7 天；`v*` 标签在全部任务成功后创建 GitHub Release。

Release Job 不检出源码，因此 `gh release create` 必须通过 `--repo "$GITHUB_REPOSITORY"` 显式指定目标仓库，不能依赖本地 `.git` 目录推断仓库名。

## 2. JAR 与 JRE 边界

构建阶段使用 `actions/setup-java@v5` 安装 Liberica Java 21 `jdk+fx` 并编译 JavaFX 源码。Maven Shade 仅聚合 Jackson、Commons Net 等非 JavaFX 运行依赖，并排除：

```text
org.openjfx:javafx-base
org.openjfx:javafx-graphics
org.openjfx:javafx-controls
org.openjfx:javafx-fxml
```

`verify` 阶段和流水线都会扫描 `ftp-synchronization.jar`。出现以下内容时立即失败：

```text
javafx/**
com/sun/javafx/**
*javafx*.dll
*javafx*.so
*javafx*.dylib
```

平台打包阶段同样使用 `actions/setup-java@v5` 安装 Liberica `jre+fx`，把该目标系统、目标 CPU 的完整 JRE 复制到 `jre/`。随后检查：

```text
javafx.base
javafx.graphics
javafx.controls
javafx.fxml
```

因此 JavaFX 只在随包 JRE 中，不在应用 JAR 中。

## 3. 工作流触发

```yaml
on:
  push:
  pull_request:
  workflow_dispatch:
```

- `pull_request`：只运行构建与测试，避免重复生成六个大包。
- `push`：测试通过后生成六个平台包。
- `workflow_dispatch`：允许从 Actions 页面手动执行。
- `v*` 标签：完成打包后创建 Release。

工作流默认权限是 `contents: read`；仅 Release 任务使用 `contents: write`。

## 4. 任务结构

### build-test

运行在 `ubuntu-24.04`：

1. 检出源码。
2. 安装 Liberica Java 21 `jdk+fx`。
3. 安装 Xvfb，为 JavaFX FXML 测试提供虚拟显示。
4. 执行 `xvfb-run -a ./mvnw -B -ntp clean verify`。
5. 再次使用 `jar tf` 检查 JAR 不含 JavaFX。
6. 读取 Maven `project.version`。
7. 上传 JAR 和 `version.txt` 给矩阵任务使用。

JAR 只构建一次，因为 Java 字节码和非 JavaFX 依赖不绑定目标系统。各平台差异全部位于随后下载的 JRE 中。

### package

矩阵使用原生运行器：

| 产物 | GitHub 运行器 | setup-java 架构 |
|---|---|---|
| Windows x64 | `windows-2022` | `x64` |
| Windows ARM64 | `windows-11-arm` | `aarch64` |
| Linux x64 | `ubuntu-24.04` | `x64` |
| Linux ARM64 | `ubuntu-24.04-arm` | `aarch64` |
| macOS x64 | `macos-15-intel` | `x64` |
| macOS ARM64 | `macos-15` | `aarch64` |

每个矩阵任务：

1. 下载已经测试过的应用 JAR。
2. 安装目标架构的 Liberica Java 21 `jre+fx`。
3. 复制 JRE、JAR、启动脚本、配置示例、文档和许可。
4. 使用 `java --list-modules` 验证四个 JavaFX 模块。
5. 启动应用并观察 8 秒；提前退出视为失败。
6. Windows 使用 ZIP，Linux/macOS 使用 TAR.GZ。
7. 生成 SHA-256 并上传为 7 天 Artifacts。

Linux 使用 Xvfb 进行 GUI 冒烟测试。Unix 压缩包使用 `tar` 保留 `run.sh` 和 `run.command` 的执行权限。

### release

仅 `refs/tags/v*` 执行：

1. 下载六个平台任务生成的压缩包和 SHA-256。
2. 使用当前任务的 `GITHUB_TOKEN` 调用 GitHub CLI。
3. 创建带自动发布说明的 Release 并上传全部附件。

## 5. 版本发布流程

Maven `pom.xml` 的 `<version>` 是唯一版本源。发布前修改版本并完成普通 push 测试，然后创建完全匹配的标签：

```bash
git tag v1.0.0
git push origin v1.0.0
```

如果标签不是 `v${project.version}`，`build-test` 会失败且不会创建 Release。

## 6. 本地复现

Windows 指定环境：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21.0.10+7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean verify
```

检查 JAR：

```powershell
& "$env:JAVA_HOME\bin\jar.exe" tf target\ftp-synchronization.jar |
  Select-String '^(javafx/|com/sun/javafx/)|javafx.*\.(dll|so|dylib)$'
```

命令不应返回任何内容。

## 7. 常见问题

### ARM64 任务排队或不可用

确认仓库账户可使用 `windows-11-arm` 和 `ubuntu-24.04-arm`。这些标签必须保持和 GitHub 当前托管运行器文档一致；如果 GitHub 更换预览标签，只修改矩阵的 `runner`，不要改变产物架构名称。

### 找不到 jre+fx

确认 `distribution: liberica`、`java-version: '21'`、`java-package: jre+fx` 和目标 `architecture` 的组合仍被 `actions/setup-java` 支持。

### GUI 冒烟测试提前退出

先查看进程标准错误，再检查 JRE 的 JavaFX 模块、压缩包工作目录和 FXML 资源。Linux 还需要确认 Xvfb 安装成功。

### Release 没有创建

依次检查标签与 Maven 版本、六个矩阵任务、Release 任务的 `contents: write` 权限，以及仓库是否允许 GitHub Actions 创建 Release。

### 压缩包过大

当前要求包含完整 `jre+fx`，因此不能删除 JavaFX 模块或 JRE 的 `legal/`。可调整普通 Artifact 的保留天数，但不要通过把 JavaFX 塞进 JAR 来缩减结构。
