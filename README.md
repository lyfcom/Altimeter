# 海拔高度计 Android APP

## 项目概述

这是一个基于Android Studio空白模板开发的海拔高度计应用，采用现代化的Android开发技术栈，提供精确的海拔高度测量功能。应用支持多种海拔数据源，包括GNSS卫星定位、气压传感器和在线海拔API，为用户提供准确可靠的海拔数据。

## 技术栈

- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose (Material Design 3)
- **架构模式**: MVVM (Model-View-ViewModel)
- **并发处理**: Kotlin Coroutines + Flow
- **网络请求**: Retrofit + Gson
- **位置服务**: Google Play Services Location API
- **权限管理**: Accompanist Permissions
- **依赖注入**: 简单工厂模式
- **最低SDK版本**: API 24 (Android 7.0)

## 项目结构

```
app/src/main/java/com/altimeter/app/
├── MainActivity.kt                          # 应用入口活动
├── data/                                    # 数据层
│   ├── models/                             # 数据模型
│   │   ├── AltitudeData.kt                # 海拔数据模型
│   │   └── AltitudeRecord.kt              # 海拔记录模型
│   ├── repository/                        # 数据仓库
│   │   └── AltitudeRecordRepository.kt    # 海拔记录仓库
│   └── services/                          # 服务层
│       ├── AltitudeService.kt             # 海拔服务接口
│       ├── LocationService.kt             # 位置服务
│       └── impl/                          # 服务实现
│           ├── BarometerAltitudeService.kt    # 气压计服务
│           ├── CompositeAltitudeServiceImpl.kt # 复合服务
│           ├── ElevationApiService.kt         # API服务
│           └── GnssAltitudeService.kt         # GNSS服务
├── ui/                                    # UI层
│   ├── components/                        # UI组件
│   │   └── AltitudeChart.kt              # 海拔图表组件
│   ├── screens/                          # 屏幕页面
│   │   ├── AltimeterScreen.kt            # 海拔计主页面
│   │   ├── HistoryScreen.kt              # 历史记录页面
│   │   ├── MainScreen.kt                 # 主界面容器
│   │   └── SettingsScreen.kt             # 设置页面
│   ├── theme/                            # 主题配置
│   │   ├── Color.kt                      # 颜色定义
│   │   ├── Theme.kt                      # 主题配置
│   │   └── Type.kt                       # 字体配置
│   └── viewmodels/                       # ViewModel层
│       └── AltimeterViewModel.kt         # 主ViewModel
└── utils/                                # 工具类
    └── GnssSystemInfo.kt                 # GNSS系统信息
```

## 核心功能模块

### 1. 数据模型 (data/models/)

#### AltitudeData.kt
海拔数据的核心模型，包含：
- `altitude: Double` - 海拔高度（米）
- `source: AltitudeSource` - 数据来源（GNSS/气压计/API等）
- `accuracy: Double` - 精度/误差范围（米）
- `reliability: Double` - 可靠度评分（0-100）
- `timestamp: LocalDateTime` - 获取时间
- `latitude/longitude: Double?` - 经纬度坐标
- `description: String` - 描述信息

**数据来源枚举 (AltitudeSource)**:
- `GNSS` - 卫星定位 (40%基础可靠度, ±10m典型精度)
- `BAROMETER` - 气压传感器 (85%基础可靠度, ±3m典型精度)
- `ELEVATION_API` - 海拔API (90%基础可靠度, ±1m典型精度)
- `NETWORK_ALTITUDE` - 网络海拔数据 (75%基础可靠度, ±5m典型精度)
- `MANUAL_INPUT` - 手动输入 (95%基础可靠度, ±0.5m典型精度)

#### AltitudeRecord.kt
海拔记录持久化模型，支持：
- 记录管理：唯一ID、时间戳、会话管理
- 测量会话：支持单次测量、实时测量、手动记录
- 统计数据：平均值、最值、范围计算
- 图表数据：时间序列数据点

### 2. 服务层 (data/services/)

#### LocationService.kt
位置服务管理类，提供：
```kotlin
// 权限检查
fun hasLocationPermission(): Boolean

// 获取当前位置（一次性）
suspend fun getCurrentLocation(): Result<LocationInfo>

// 获取位置更新流（实时位置）
fun getLocationUpdates(updateIntervalMs: Long = 5000): Flow<LocationInfo>
```

#### AltitudeService接口
定义海拔服务的标准接口：
```kotlin
interface AltitudeService {
    suspend fun getAltitude(location: LocationInfo): Result<AltitudeData>
    fun getServiceName(): String
    suspend fun isAvailable(): Boolean
}
```

#### 服务实现类

**BarometerAltitudeService.kt** - 气压传感器服务
- 使用设备气压传感器计算海拔
- 基于国际标准大气模型: `H = 44330 * (1 - (P/P0)^(1/5.255))`
- 动态可靠度评估（基于大气压范围）

**GnssAltitudeService.kt** - GNSS卫星定位服务
- 支持GPS、北斗、GLONASS、Galileo等多种卫星系统
- 基于定位精度动态计算可靠度
- 直接使用系统提供的海拔数据

**ElevationApiService.kt** - 在线海拔API服务
- 使用Open Elevation API获取海拔数据
- Retrofit + 协程异步处理
- 基于海拔范围进行可靠度评估

**CompositeAltitudeServiceImpl.kt** - 复合服务管理器
- 并行调用多个海拔服务
- 智能评分算法：可靠度70% + 精度30%
- 返回最佳海拔数据

### 3. 数据仓库 (data/repository/)

#### AltitudeRecordRepository.kt
海拔记录管理仓库，功能包括：
- **记录管理**: 添加、删除、查询海拔记录
- **会话管理**: 支持测量会话的创建、更新、结束
- **数据流**: 使用StateFlow提供响应式数据
- **统计分析**: 自动计算统计数据和图表数据
- **时间筛选**: 支持时间范围查询

主要方法：
```kotlin
suspend fun addRecord(altitudeData: AltitudeData, sessionType: SessionType): AltitudeRecord
fun startNewSession(sessionType: SessionType): String
suspend fun endCurrentSession()
fun getRecordsInTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<AltitudeRecord>>
fun getChartDataPoints(maxPoints: Int = 100): Flow<List<ChartDataPoint>>
fun getStatistics(): Flow<AltitudeStatistics>
```

### 4. ViewModel层 (ui/viewmodels/)

#### AltimeterViewModel.kt
应用的核心ViewModel，管理：

**状态管理**:
- `measurementState` - 当前测量状态
- `currentLocation` - 当前位置信息
- `hasLocationPermission` - 权限状态
- `isAutoRecordEnabled` - 自动记录开关

**主要功能**:
```kotlin
// 单次海拔测量
fun startSingleMeasurement()

// 实时测量切换
fun toggleRealTimeMeasurement()

// 设置更新间隔
fun setUpdateInterval(intervalMs: Long)

// 记录管理
fun addManualRecord(altitudeData: AltitudeData)
fun clearAllRecords()
fun deleteRecord(recordId: String)
```

**测量流程**:
1. 权限检查 → 位置获取 → 海拔测量 → 数据展示 → 自动记录

### 5. UI层 (ui/)

#### 主界面结构 (screens/)

**MainScreen.kt** - 主界面容器
- 底部导航栏设计
- Navigation组件管理页面跳转
- 共享ViewModel实例

**AltimeterScreen.kt** - 海拔计主页面
- 权限请求处理
- 控制按钮区域（测量/实时/清除）
- 状态指示器（定位中/测量中/成功/错误）
- 位置信息卡片
- 海拔数据列表（可靠度排序，最佳数据标记）

**HistoryScreen.kt** - 历史记录页面
- 三标签页设计：图表/统计/记录
- 时间范围筛选（24小时/7天/30天/全部）
- 自动记录开关
- 数据导出功能接口
- 记录删除功能

**SettingsScreen.kt** - 设置页面
- 实时更新间隔配置
- 数据源说明
- GNSS系统支持信息
- 使用提示和帮助

#### 自定义组件 (components/)

**AltitudeChart.kt** - 海拔图表组件
- Canvas绘制的自定义图表
- 支持多数据源颜色区分
- 渐变填充和平滑连线
- 点击交互和详情弹窗
- 动画效果支持
- 网格线和坐标轴标签

图表特性：
- 数据点颜色编码（GNSS蓝色、气压计绿色、API橙色）
- 动画进入效果
- 点击数据点查看详情
- 自适应Y轴范围
- 时间轴标签

### 6. 工具类 (utils/)

#### GnssSystemInfo.kt
GNSS系统信息管理工具，包含：
- 支持的卫星系统列表（GPS、北斗、GLONASS、Galileo、QZSS、IRNSS）
- 系统详细信息（运营国家、描述、星座名称）
- 支持说明文本生成

## 应用特性

### 多数据源融合
- **并行获取**: 同时从多个数据源获取海拔数据
- **智能评分**: 综合可靠度和精度进行评分排序
- **最佳选择**: 自动识别和标记最可靠的数据

### 实时测量
- **位置流**: 基于Google Play Services的位置更新流
- **可配置间隔**: 1秒到30秒的更新间隔选择
- **会话管理**: 实时测量会话的开始、暂停、结束
- **自动记录**: 实时测量过程中自动保存数据

### 数据可视化
- **实时图表**: 海拔高度随时间变化的折线图
- **多源显示**: 不同颜色区分不同数据源
- **交互功能**: 点击数据点查看详细信息
- **时间筛选**: 支持不同时间范围的数据查看
- **自定义日期**: 支持选择特定起止日期进行数据筛选
- **图表优化**: Y轴标签正确对齐，时间轴清晰显示

### 历史记录管理
- **会话分组**: 按测量会话组织记录，正确统计会话数量
- **统计分析**: 自动计算平均值、最值、范围等统计数据
- **记录操作**: 支持单条记录删除和批量清空
- **数据分享**: 支持文本摘要、详细记录和CSV文件多种分享格式
- **自定义筛选**: 支持按自定义日期范围筛选历史数据

### 权限和安全
- **渐进式权限**: 使用Accompanist Permissions库
- **权限状态管理**: 实时监控权限状态变化
- **错误处理**: 完善的异常处理和用户提示

## 数据流架构

```
UI Layer (Compose)
    ↕ (StateFlow/Flow)
ViewModel Layer (AltimeterViewModel)
    ↕ (suspend functions)
Repository Layer (AltitudeRecordRepository)
    ↕ (service calls)
Service Layer (CompositeAltitudeService)
    ↕ (parallel calls)
Data Sources (GNSS/Barometer/API)
```

## 依赖项分析

### 主要依赖
```gradle
// Compose BOM
implementation platform('androidx.compose:compose-bom:2023.10.01')

// Compose核心
implementation 'androidx.compose.ui:ui'
implementation 'androidx.compose.material3:material3'
implementation 'androidx.compose.ui:ui-tooling-preview'

// 导航
implementation 'androidx.navigation:navigation-compose:2.7.5'

// ViewModel
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'

// 位置服务
implementation 'com.google.android.gms:play-services-location:21.0.1'

// 网络请求
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// 权限
implementation 'com.google.accompanist:accompanist-permissions:0.32.0'

// 协程
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

## 性能优化

### 内存管理
- StateFlow替代LiveData，减少内存泄漏
- LazyColumn懒加载长列表
- 图表Canvas重用，避免频繁重绘

### 网络优化
- Retrofit协程支持，避免阻塞主线程
- API服务可用性检测，减少无效请求
- 超时和重试机制

### 电量优化
- 可配置的位置更新间隔
- 智能传感器管理
- 后台任务优化

### 存储优化
- **智能数据管理**: 自动限制最大记录数（5000条）和会话数（500个）
- **数据压缩**: 移除空描述字段，减少存储空间占用
- **分批清理**: 当数据量超过80%阈值时自动清理旧数据
- **大小监控**: 实时监控存储大小，超过1MB时启用压缩存储
- **故障恢复**: 存储失败时自动降级保存核心数据
- **启动优化**: 应用启动时检查并清理过量数据

## 测试策略

### 单元测试覆盖
- ViewModel业务逻辑测试
- Repository数据操作测试
- Service层功能测试
- 工具类方法测试

### 集成测试
- 多服务协作测试
- 权限流程测试
- 数据流完整性测试

### UI测试
- Compose UI测试
- 导航流程测试
- 用户交互测试

## 扩展性设计

### 插件化架构
- AltitudeService接口支持新数据源扩展
- CompositeService动态服务管理
- 模块化的服务实现

### 配置化
- 更新间隔可配置
- 数据源优先级可调整
- UI主题可扩展

### 国际化支持
- 字符串资源分离
- 多语言界面支持预留
- 本地化格式处理

## 部署要求

### 系统要求
- Android 7.0 (API level 24) 及以上
- 位置权限 (ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION)
- 网络权限 (INTERNET)
- 可选：气压传感器硬件支持

### 权限清单
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
```

### 硬件要求
- GPS/GNSS接收器（必需）
- 气压传感器（可选，提高精度）
- 网络连接（API数据源）

## 开发指南

### 环境配置
1. Android Studio Flamingo及以上版本
2. Kotlin 1.9.0及以上
3. Gradle 8.0及以上
4. JDK 17

### 代码规范
- 使用Kotlin官方代码风格
- 函数命名采用驼峰命名法
- 类注释使用KDoc格式
- 异步操作使用协程

### 添加新数据源
1. 实现`AltitudeService`接口
2. 在`CompositeAltitudeServiceImpl`中注册
3. 在`AltitudeSource`枚举中添加新类型
4. 更新UI显示逻辑

### 自定义UI组件
1. 继承Compose组件规范
2. 支持Material Design 3主题
3. 提供完整的状态管理
4. 包含无障碍支持

## 故障排除

### 常见问题
1. **定位失败**: 检查权限设置和GPS开关
2. **气压数据异常**: 验证设备传感器支持
3. **API请求失败**: 确认网络连接和API可用性
4. **应用崩溃**: 查看日志中的异常信息
5. **数据丢失**: 数据已自动持久化存储，重启应用后会自动恢复
6. **存储空间不足**: 应用会自动清理旧数据，保留最新记录
7. **分享功能异常**: 检查是否有其他应用可接收分享内容

### 调试技巧
- 使用Android Studio Profiler监控性能
- 启用位置mock进行测试
- 使用Logcat查看详细日志
- Network Inspector监控API请求
- 检查SharedPreferences中的数据存储状态

### 数据管理
- **存储位置**: 数据存储在应用私有的SharedPreferences中
- **数据格式**: 使用JSON格式序列化存储
- **容量限制**: 记录上限5000条，会话上限500个
- **清理策略**: 超出限制时保留最新数据，按时间倒序清理
- **恢复方法**: 清除应用数据可重置所有设置和记录

## 版本历史

### v1.0.0 (当前版本)
- 基础海拔测量功能
- 多数据源支持
- 实时测量和历史记录
- Material Design 3界面
- 图表可视化

### v1.1.0 (最新更新)
**修复的问题:**
- ✅ 修复海拔图表Y轴标签显示异常问题
- ✅ 修复时间轴横坐标消失问题
- ✅ 修复数据无法持久化存储问题
- ✅ 修复海拔范围一直显示0.0的问题
- ✅ 修复总测量会话数量显示为0的问题
- ✅ 修复分享按钮无响应问题

**新增功能:**
- 🆕 数据持久化存储：使用SharedPreferences实现本地数据存储
- 🆕 自定义日期范围选择：支持选择特定时间段查看数据
- 🆕 智能数据管理：自动清理过量数据，避免存储溢出
- 🆕 多种分享选项：支持文本、CSV文件等多种分享格式
- 🆕 会话管理：正确管理单次测量和实时测量会话
- 🆕 存储监控：实时监控存储使用情况

**技术改进:**
- 📈 图表布局优化：Y轴标签正确对齐，时间轴清晰显示
- 💾 存储性能优化：压缩存储、分页管理、自动清理机制
- 🔄 会话生命周期管理：单次测量自动创建和结束会话
- 📊 统计数据修复：海拔范围和会话统计准确计算
- 🎯 UI交互改进：更直观的日期选择和分享选项

### 未来规划
- [ ] 离线地图集成
- [ ] 轨迹记录功能
- [ ] 云同步支持
- [ ] Widget小部件
- [ ] 海拔预警功能
- [ ] 数据备份恢复

## 贡献指南

### 开发流程
1. Fork项目仓库
2. 创建功能分支
3. 编写代码和测试
4. 提交Pull Request
5. Code Review和合并

### 代码提交规范
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式调整
- refactor: 代码重构
- test: 测试相关
- chore: 构建配置等

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issues]
- 技术支持: [Contact Email]

---

**注**: 这是一个技术文档，旨在帮助开发者理解和参与项目开发。如有疑问，请通过Issue或联系方式获取帮助。