# KurisuAPI 项目说明

## 项目信息

| 项目 | 值 |
|------|-----|
| 包名 | `com.kurisuapi` |
| compileSdk | 36 |
| minSdk | 26 |
| targetSdk | 35 |
| versionCode | 9 |
| versionName | `0.0.1` |
| AGP | `8.7.3` |
| Kotlin | `2.2.21` |
| DI | Hilt + KSP |
| 数据库 | Room + SQLite-Vector |
| UI | Jetpack Compose + Material 3 |

> **说明**：versionName 尚未随迭代更新，打包时以 `app/build.gradle.kts` 中的值为准。

---

## 打包 APK 标准（必须严格遵守）

当用户说"打包"时，按以下流程执行，不可跳过任何步骤：

### 1. 构建
```bash
./gradlew assembleRelease
```

### 2. 读取版本号
从 `app/build.gradle.kts` 中提取 `versionName`。

### 3. 复制到桌面
```bash
cp app/build/outputs/apk/release/app-release.apk "/Users/yuki/Desktop/KurisuAPI-正式版v{versionName}.apk"
```

### 规则汇总
| 项目 | 标准 |
|------|------|
| 构建命令 | `assembleRelease`（禁止 `assembleDebug`） |
| 文件命名 | `KurisuAPI-正式版v{versionName}.apk` |
| 输出位置 | `/Users/yuki/Desktop/` |
| 同名文件 | 直接覆盖，无需确认 |
| 触发条件 | 仅用户明确说"打包"时触发，不自动构建 |

---

## 签名说明

- 密钥库：`release.keystore`，别名 `kurisuapi`
- 凭据存于 `local.properties`（`RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`），不在 VCS 中
- debug 和 release 均使用同一 release 签名

---

## UI 改动的红线

做任何 UI 风格调整时：
- **绝对不能**未经用户同意删除按钮、开关、输入框等可交互组件
- 改动前必须列出所有交互组件及其功能，征得用户同意后再动手

---

## 项目结构速览

```
com.kurisuapi/
├── KurisuApp.kt              # Application，Hilt + WorkManager + 日记定时
├── MainActivity.kt           # 入口 Activity，EdgeToEdge + 主题 + 屏幕缩放
├── data/
│   ├── api/                  # AiApiService（Retrofit）
│   ├── dao/                  # 15+ Room DAO
│   ├── database/             # KurisuDatabase
│   ├── entity/               # 17+ Entity
│   ├── repository/           # 15+ Repository（SettingsRepository 363 行）
│   └── wechat/               # 微信集成（WeChatApiService, WeChatRepository）
├── di/                       # Hilt 模块（DatabaseModule 500 行）
├── domain/
│   ├── bridge/               # MessageBridge, WeChatBridge（906 行）
│   ├── engine/               # EmotionEngine, MemoryExtractor, PromptBuilder（1079 行）,
│   │                           RelationshipEngine, CircadianModulator 等
│   ├── provider/             # AnthropicProvider, GeminiProvider, OpenAiCompatibleProvider
│   └── service/              # AiService, ModelRegistryService
├── service/                  # BootReceiver, DiaryWorker, WeChatBotService
├── ui/
│   ├── component/            # ChatBubble, FrostedGlassSurface, LiquidGlassSurface,
│   │                           LiquidGlassContainer, EulaDialog, ColorPickerSheet 等
│   ├── navigation/           # MainScreen（4 Tab：首页/日记/对话/我），Screen 路由
│   ├── screen/               # 10+ 页面
│   ├── theme/                # KurisuAPITheme, Apple 色值, MaterialKolor 配色
│   └── viewmodel/            # 13+ ViewModel
└── util/                     # ScreenScale(sdp/ssp), LogManager, TokenEstimator, EncryptedSettings
```
