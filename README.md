# KurisuAPI / 栗子API

[中文](#中文) | [English](#english)

---

## 中文

AI 聊天伴侣 Android 应用，支持微信机器人、角色扮演与情感记忆系统。开箱即用，半成品。

### 功能

- **多 AI 提供商 / Multi-Provider** — 支持 Anthropic (Claude)、Gemini、OpenAI 兼容接口，可在应用内切换
- **角色扮演 / Role-Play** — 自定义 AI 角色的人设、性格、说话风格
- **长期记忆 / Long-Term Memory** — AI 会记住你们聊过的重要内容，越聊越懂你
- **情感引擎 / Emotion Engine** — AI 会根据对话内容产生情绪反应
- **关系系统 / Relationship System** — AI 与你的关系会随着互动逐渐变化
- **日记功能 / Diary** — 自动生成日记，记录与 AI 的互动
- **微信机器人 / WeChat Bot** — 可接入微信，让 AI 替你回复消息
- **向量搜索 / Vector Search** — 基于语义的记忆检索
- **液态玻璃 UI / Liquid Glass UI** — iOS 风格毛玻璃界面

### 技术栈 / Tech Stack

| 分类 Category | 技术 Tech |
|---------------|-----------|
| 语言 Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| 数据库 Database | Room + SQLite-Vector (向量搜索 Vector Search) |
| 网络 Network | Retrofit + OkHttp |
| 构建 Build | Gradle + KSP |

### 项目结构 / Project Structure

```
com.kurisuapi/
├── data/           # 数据层 Data Layer：API、DAO、Entity、Repository
├── di/             # Hilt 依赖注入模块 Dependency Injection
├── domain/         # 业务逻辑 Domain：AI 引擎、情感引擎、记忆系统、微信桥接
├── service/        # Android 服务 Services：启动、日记定时、微信机器人
├── ui/             # 界面 UI：屏幕、组件、主题、ViewModel
└── util/           # 工具类 Utilities
```

### 构建 / Build

1. 用 Android Studio 打开项目 / Open with Android Studio
2. 创建 `local.properties`，填入签名信息（或跳过签名用 debug 构建）：
   ```properties
   sdk.dir=/path/to/Android/sdk
   RELEASE_STORE_PASSWORD=your_password
   RELEASE_KEY_ALIAS=kurisuapi
   RELEASE_KEY_PASSWORD=your_password
   ```
3. 生成自己的 `release.keystore`（或修改 `build.gradle.kts` 去掉 release 签名）
4. `./gradlew assembleDebug`

或者直接用 Android Studio 点运行 / Or just click Run in Android Studio.

### 许可证 / License

本项目使用 **GNU General Public License v3.0 (GPL 3.0)**。详见 [LICENSE](LICENSE) 文件。

This project is licensed under **GPL 3.0**. See [LICENSE](LICENSE).

---

## English

An AI companion Android app with WeChat bot integration, role-play, and emotional memory system. Works out of the box. Prototype stage.

### Features

- **Multi-Provider AI** — Anthropic (Claude), Gemini, OpenAI-compatible. Switch on the fly.
- **Role-Play** — Customize character personality, backstory, and speech style.
- **Long-Term Memory** — The AI remembers what matters from your conversations.
- **Emotion Engine** — The AI reacts emotionally to your messages.
- **Relationship System** — Your bond with the AI evolves through interaction.
- **Diary** — Auto-generated journal entries from your AI interactions.
- **WeChat Bot** — Connect to WeChat for auto-reply.
- **Vector Search** — Semantic memory retrieval.
- **Liquid Glass UI** — iOS-inspired frosted glass interface.

### Tech Stack

| Category | Tech |
|----------|------|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room + SQLite-Vector |
| Network | Retrofit + OkHttp |
| Build | Gradle + KSP |

### Project Structure

```
com.kurisuapi/
├── data/           # API, DAO, Entity, Repository
├── di/             # Hilt modules
├── domain/         # AI Engine, Emotion Engine, Memory, WeChat Bridge
├── service/        # Boot Receiver, Diary Worker, WeChat Bot Service
├── ui/             # Screens, Components, Theme, ViewModels
└── util/           # Utilities
```

### Build

Same as Chinese section above. Quick start: open with Android Studio and hit Run.

### License

**GPL 3.0**. See [LICENSE](LICENSE).
