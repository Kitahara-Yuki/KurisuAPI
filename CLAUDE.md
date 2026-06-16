# KurisuAPI 项目说明

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
cp app/build/outputs/apk/release/app-release.apk "/Users/yuki/Desktop/KurisuAPI-v{versionName}.apk"
```

### 规则汇总
| 项目 | 标准 |
|------|------|
| 构建命令 | `assembleRelease`（禁止 `assembleDebug`） |
| 文件命名 | `KurisuAPI-v{versionName}.apk`（英文名，不要中文） |
| 输出位置 | `/Users/yuki/Desktop/` |
| 同名文件 | 直接覆盖，无需确认 |
| 触发条件 | 仅用户明确说"打包"时触发，不自动构建 |

## UI 改动的红线

做任何 UI 风格调整时：
- **绝对不能**未经用户同意删除按钮、开关、输入框等可交互组件
- 改动前必须列出所有交互组件及其功能，征得用户同意后再动手
