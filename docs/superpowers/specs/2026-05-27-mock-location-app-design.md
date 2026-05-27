# Android 定位修改 App 设计文档

## 概述

开发一款 Android 手机端应用，通过系统 Mock Location API 修改手机 GPS 定位，主要用于社交/打卡场景。App 采用底部 Tab 导航，包含地图选点、收藏位置、历史记录三个核心模块。

后续阶段将开发电脑端工具，通过 USB 连接支持 iOS 设备的位置修改。

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **地图 SDK**: 高德地图 SDK (com.amap.api:3dmap)
- **搜索 SDK**: 高德搜索服务 (com.amap.api:search)
- **UI 框架**: Material Design 3 (Material 3)
- **架构**: MVVM + Repository Pattern
- **异步**: Kotlin Coroutines + Flow
- **数据库**: Room
- **导航**: Navigation Component
- **构建**: Gradle Kotlin DSL

## 核心页面

### Tab 1 - 地图选点

- 全屏高德地图，支持缩放、拖动
- 点击地图任意位置选择定位点，显示红色标记
- 顶部搜索栏，支持地址关键词搜索和经纬度坐标输入
- 搜索结果以下拉列表展示，点击后地图跳转到对应位置
- 底部信息卡片：
  - 显示选中位置的名称和完整地址
  - 显示经纬度坐标
  - 红色"开始模拟定位"按钮
- 模拟进行中时：
  - 按钮变为"停止模拟"
  - 地图标记变为蓝色脉冲动画
  - 底部卡片显示当前模拟位置信息

### Tab 2 - 收藏位置

- 列表展示所有收藏的位置
- 每项显示：自定义名称、地址、一键"定位"按钮
- 点击"定位"直接开始模拟到该位置
- 长按列表项弹出编辑/删除菜单
- 右上角"+"按钮添加新收藏（跳转地图选点）
- 编辑支持修改名称和位置
- 收藏上限 50 条

### Tab 3 - 历史记录

- 按使用时间倒序展示所有模拟定位记录
- 每项显示：位置名称、使用时间、"再用"按钮
- 点击"再用"直接开始模拟到该位置
- 左滑删除单条记录
- 顶部"清除全部"按钮（需二次确认）
- 历史记录上限 200 条，超出自动删除最旧的记录

## 核心机制

### Mock Location 实现

1. App 声明 `android.permission.ACCESS_MOCK_LOCATION` 权限
2. 首次使用时检测是否已设置为模拟位置应用：
   - 未设置：引导用户前往 开发者选项 → 选择模拟位置信息应用 → 选择本 App
   - 已设置：正常使用
3. 使用 `LocationManager.addTestProvider()` 注册测试位置提供者
4. 使用 `LocationManager.setTestProviderLocation()` 注入模拟位置
5. 模拟期间以 1 秒间隔持续注入位置更新，确保其他 App 能稳定获取
6. 停止模拟时调用 `LocationManager.removeTestProvider()` 清理

### 前台服务

- 模拟定位期间启动前台服务 (Foreground Service)
- 常驻通知栏显示"正在模拟定位: xxx"
- 通知包含"停止模拟"操作按钮
- 点击通知跳转回 App
- 前台服务确保模拟不被系统杀死

### 权限处理

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: 获取当前位置（用于地图初始定位）
- `ACCESS_MOCK_LOCATION`: 模拟定位核心权限
- `POST_NOTIFICATIONS`: Android 13+ 通知权限
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION`: 前台服务

## 数据模型

### FavoriteLocation (收藏位置)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (auto) | 主键 |
| name | String | 自定义名称 |
| address | String | 完整地址 |
| latitude | Double | 纬度 |
| longitude | Double | 经度 |
| createdAt | Long | 创建时间戳 |

### LocationHistory (历史记录)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (auto) | 主键 |
| name | String | 位置名称 |
| address | String | 完整地址 |
| latitude | Double | 纬度 |
| longitude | Double | 经度 |
| usedAt | Long | 使用时间戳 |

## 项目结构

```
app/src/main/java/com/mocklocation/app/
├── App.kt                           # Application 类
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt           # Room 数据库
│   │   ├── dao/
│   │   │   ├── FavoriteDao.kt       # 收藏 DAO
│   │   │   └── HistoryDao.kt        # 历史 DAO
│   │   └── entity/
│   │       ├── FavoriteLocation.kt  # 收藏实体
│   │       └── LocationHistory.kt   # 历史实体
│   └── repository/
│       ├── FavoriteRepository.kt    # 收藏仓库
│       └── HistoryRepository.kt     # 历史仓库
├── service/
│   └── MockLocationService.kt       # 模拟定位前台服务
├── ui/
│   ├── MainActivity.kt              # 主 Activity
│   ├── map/
│   │   ├── MapFragment.kt           # 地图选点页
│   │   └── MapViewModel.kt          # 地图页 ViewModel
│   ├── favorite/
│   │   ├── FavoriteFragment.kt      # 收藏页
│   │   └── FavoriteViewModel.kt     # 收藏页 ViewModel
│   ├── history/
│   │   ├── HistoryFragment.kt       # 历史页
│   │   └── HistoryViewModel.kt      # 历史页 ViewModel
│   └── common/
│       └── LocationMapper.kt        # 位置数据映射工具
└── util/
    ├── MockLocationManager.kt       # 模拟定位管理器
    └── PermissionHelper.kt          # 权限检查工具
```

## 错误处理

- 未授予位置权限：弹出权限请求对话框
- 未设置模拟位置应用：显示引导页面，带跳转按钮
- 地图加载失败：显示重试提示
- 搜索无结果：显示"未找到相关位置"提示
- 模拟定位失败：Toast 提示错误原因
- 数据库操作失败：静默重试，不崩溃

## 后续阶段（不在本次实现范围）

- 阶段二：电脑端工具（Python + PyQt），通过 USB + libimobiledevice 修改 iOS 设备定位
- 阶段二同时支持通过 ADB 修改 Android 设备定位（作为电脑端备选方案）
