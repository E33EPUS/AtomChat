# AtomChat

Fabric 1.21.1 客户端聊天模组：用 Skija 渲染的手机 App 风格聊天面板。

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

## 依赖

- Minecraft 1.21.1 + Fabric Loader + Fabric API
- Java 21+
- Skija `0.116.8` / FlatLaf `3.7.2`（构建时打入 JAR）
