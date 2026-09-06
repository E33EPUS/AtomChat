# AtomChat

[![Release](https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver&label=%E6%9C%80%E6%96%B0%E7%89%88)](https://github.com/E33EPUS/AtomChat/releases/latest)
[![Build](https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg)](https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml)

为 Minecraft 带来手机 App 风格的聊天体验（Fabric 1.21.1，Skia 渲染）。

> 本分支 `master` 是**源码分支**。完整用户文档（中文 / English）位于默认分支 [Master/README.md](https://github.com/E33EPUS/atomchat/blob/Master/README.md)。

## 构建

```bash
./gradlew.bat build
```

产物：`build/libs/atomchat-Fabric-1.21.1-<version>.jar`

## 测试

```bash
./gradlew.bat test
```

## 下载

- GitHub Releases：<https://github.com/E33EPUS/AtomChat/releases/latest>
- Modrinth / CurseForge：项目公开后在此补充链接

## 依赖

- Minecraft 1.21.1 + Fabric Loader + Fabric API
- Java 21+
- Skija `0.116.8` / FlatLaf `3.7.2`（构建时打入 JAR）
