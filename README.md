# Altimeter · 海拔计

一个离线、无需 Google Play 服务的 Android 高度仪表。关注两件事：**读数代表什么，以及记录是否可信**。

- 原生 Kotlin / Jetpack Compose，浅色、深色与系统主题。
- 卫星高度、气压测高、相对归零；显示真实高度基准和可用的垂直精度。
- 实时曲线，前台服务持续记录，行程统计与 CSV / GPX 导出。
- 无账号、无广告、无网络权限。应用不上传定位和传感器数据；系统备份遵循设备设置。

## 测量行为

| 情况 | 行为 |
|---|---|
| 有效的气压校准 | 自动模式优先采用校准后的气压高度；不虚构精度数值 |
| 无校准、有有效卫星高度 | 使用卫星高度；Android 14+ 尝试系统离线海平面修正 |
| 无海平面修正 | 明确标记 WGS84 椭球高度，不能当作海拔 |
| 只有气压计 | 显示相对高度，可归零，无需定位权限 |
| 定位超过 15 秒、气压超过 5 秒 | 该数据源失效，不继续展示为实时值 |
| 校准超过 12 小时，或系统时间回退至校准之前 | 校准失效，要求重新校准 |
| 行程中信号消失 | 保留间断；固定最初数据源与高度基准，不拼接异构高度 |
| 进程被系统终止 | 下次启动保留已提交的采样，将未结束行程标为“中断” |

气压高度会受天气影响，卫星垂直精度通常弱于水平精度。累计爬升使用噪声阈值、间断检测和跳变过滤，是估计值，可能忽略细小变化。本应用不是航空或专业测绘仪器。

## 使用

1. 打开仪表即可读取可用气压；允许精确定位后接收卫星数据。
2. 在已知标高处选择“校准高度”。没有可靠标高时可以使用相对高度。
3. 点击“开始记录”；Android 的位置前台服务要求授予位置权限。锁屏后通过通知或应用结束记录。
4. 在“行程”查看曲线并导出。CSV 保留全部点；GPX 仅包含有坐标的点，相对/椭球高度放入扩展字段而不是标准 `<ele>`。

记录时更改单位、主题不影响原始数据。数据源、校准和采样间隔在记录期间锁定。

## 构建

Android 7.0（API 24）及以上；`compileSdk / targetSdk 36`。使用 JDK 17 或 21、Android SDK 36、Build Tools 35.0.0。

```bash
./gradlew :core:test :app:assembleDebug :app:lintDebug
# 连接设备或启动模拟器后：
./gradlew :app:connectedDebugAndroidTest
# 未签名的压缩 release 包：
./gradlew :app:assembleRelease
```

Android Studio 可直接打开根目录。通过 SDK Manager 安装所需 SDK，或设置 `ANDROID_HOME`。发行签名由维护者配置，仓库不包含签名密钥或预构建 APK。

依赖统一在 `gradle/libs.versions.toml` 固定版本；Gradle Wrapper 校验发行包 SHA-256。CI 构建 debug/release，运行核心测试、Lint 和模拟器测试，并上传 APK 与报告。

## 结构

```text
core/                         纯 Kotlin，无 Android 依赖
  Measurement.kt              高度基准、校准、数据新鲜度、气压滤波
  Track.kt                    爬升统计、流式 CSV / GPX
app/
  data/                       Room 行程与采样、DataStore 设置、分页导出
  measurement/                SensorFeed 边界、Android 传感器接入、测量状态
  recording/                  记录控制器、Android 前台服务
  ui/                         仪表、历史、设置、图表、主题与 UI 状态
  AltimeterApplication.kt     显式依赖组装
```

完整设计取舍见 [架构说明](docs/architecture.md)，设备验证见 [验证清单](docs/validation.md)。

## 2.0 重构边界

2.0 按全新产品实现，使用新的 Room 数据库；不导入 1.x 的 SharedPreferences 历史，也不删除其旧数据。保留包名，版本号提升为 5。正式覆盖安装需要维护者使用原签名。

移除了自动在线地形查询、硬编码“可靠度”、Google Play 定位依赖、Accompanist 权限库、MPAndroidChart 和重复服务实例。地形海拔不是设备高度，不参与高度选择。
