# KurisuAPI UI 风格规范

> 苹果液态玻璃风格。所有界面改动、新页面创建，都必须严格遵循本文档。

---

## 1. 配色

### 苹果系统强调色
| 名称 | 色值 | 用途 |
|------|------|------|
| AppleBlue | `#007AFF` | 主色、链接、选中态 |
| AppleGreen | `#34C759` | 成功、连接、开心 |
| AppleOrange | `#FF9500` | 警告、中等 |
| AppleRed | `#FF3B30` | 错误、删除、生气 |
| AppleGray | `#8E8E93` | 辅助文字 |
| ApplePink | `#FF2D55` | 好感 |
| AppleIndigo | `#5856D6` | 孤独 |

### 毛玻璃表面色
- 白天：`GlassWhite = Color(0xCCFFFFFF)` — 80% 透白
- 黑夜：`GlassDark = Color(0xCC1C1C1E)` — 80% 透黑

---

## 2. 主题

- 双主题（白天/黑夜），跟随系统自动切换，`dynamicColor = false`
- 全局圆角：`Shapes(small = 12dp, medium = 16dp, large = 20dp)`
- Typography：使用 Material3 默认

```kotlin
// Theme.kt — 配置不变
MaterialTheme(
    colorScheme = colorScheme,
    shapes = GlassShapes,
    typography = Typography(),
    content = content
)
```

---

## 3. 卡片

所有卡片统一写法，三个要点缺一不可：

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ),
    shape = MaterialTheme.shapes.medium  // 16dp 圆角
) {
    // 内容
}
```

---

## 4. 图标

**只用 `Icons.Outlined.*`**，禁止 `Icons.Default.*`（太粗，不符合苹果风格）。

```kotlin
// ✅ 正确
Icon(Icons.Outlined.Home, ...)
Icon(Icons.Outlined.Person, ...)

// ❌ 错误
Icon(Icons.Default.Home, ...)
```

---

## 5. 顶部导航栏

```kotlin
TopAppBar(
    title = { Text("标题", fontWeight = FontWeight.SemiBold) },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    )
)
```

---

## 6. 屏幕自适应缩放

**所有 dp 值必须用 `sdp()` 包裹**，以 360dp 宽为基准自动缩放：

```kotlin
// ✅ 正确
Modifier.padding(sdp(16.dp))
Arrangement.spacedBy(sdp(8.dp))
Modifier.size(sdp(24.dp))

// ❌ 错误
Modifier.padding(16.dp)
```

例外（不缩放）：
- `0.5.dp` 边框线宽
- `Theme.kt` 中的 `GlassShapes`（全局圆角）

```kotlin
// import 必须加上
import com.kurisuapi.util.sdp
```

缩放范围：小屏 0.85x ～ 标准 1.0x ～ 大屏 1.2x

---

## 7. 状态栏

- `MainScreen` 的 `contentWindowInsets = WindowInsets(0)`，内层 Scaffold 各自处理
- `themes.xml` 中状态栏/导航栏已设为透明
- `MainActivity` 已调用 `enableEdgeToEdge()`

新页面中的 `Scaffold` 无需额外处理，默认行为即可让 TopAppBar 延伸到状态栏后面。

---

## 8. GlassSurface 组件

项目中有 `util/GlassSurface.kt`，封装了毛玻璃背景、边框、圆角。API 31+ 支持真模糊，低版本半透明降级。创建新玻璃组件时优先用它：

```kotlin
GlassSurface(
    blurRadius = 25.dp,     // 模糊半径（API 31+）
    cornerRadius = sdp(16.dp),  // 圆角
    modifier = Modifier.fillMaxWidth()
) {
    // 内容
}
```

---

## 9. 改动检查清单

改任何 UI 代码前，确认以下所有项：

- [ ] 配色来自 `com.kurisuapi.ui.theme`（Apple 色值）
- [ ] 图标用 `Icons.Outlined.*`
- [ ] 卡片遵循第 3 节写法
- [ ] dp 值用 `sdp()` 包裹
- [ ] 有 `import com.kurisuapi.util.sdp`
- [ ] TopAppBar 用半透明背景
- [ ] `FontWeight.SemiBold` 而非 `Bold`
- [ ] 编译通过
