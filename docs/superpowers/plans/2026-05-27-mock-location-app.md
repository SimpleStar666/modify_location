# Android 定位修改 App 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 开发一款 Android 原生 App，通过 Mock Location API 修改手机 GPS 定位，支持地图选点、收藏位置、历史记录三个核心功能。

**Architecture:** MVVM + Repository Pattern，使用 Kotlin + Jetpack 组件。地图选点页集成高德地图 SDK，模拟定位通过前台服务持续注入 Mock Location 数据，Room 数据库持久化收藏和历史记录。

**Tech Stack:** Kotlin, Android SDK (min 24 / target 34), 高德地图 SDK (3dmap + search), Material Design 3, Room, Navigation Component, Kotlin Coroutines + Flow, Gradle Kotlin DSL

---

## File Structure

```
app/
├── build.gradle.kts
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── java/com/mocklocation/app/
│       │   ├── App.kt
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── dao/
│       │   │   │   │   ├── FavoriteDao.kt
│       │   │   │   │   └── HistoryDao.kt
│       │   │   │   └── entity/
│       │   │   │       ├── FavoriteLocation.kt
│       │   │   │       └── LocationHistory.kt
│       │   │   └── repository/
│       │   │       ├── FavoriteRepository.kt
│       │   │       └── HistoryRepository.kt
│       │   ├── service/
│       │   │   └── MockLocationService.kt
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── map/
│       │   │   │   ├── MapFragment.kt
│       │   │   │   └── MapViewModel.kt
│       │   │   ├── favorite/
│       │   │   │   ├── FavoriteFragment.kt
│       │   │   │   └── FavoriteViewModel.kt
│       │   │   ├── history/
│       │   │   │   ├── HistoryFragment.kt
│       │   │   │   └── HistoryViewModel.kt
│       │   │   └── common/
│       │   │       └── LocationMapper.kt
│       │   └── util/
│       │       ├── MockLocationManager.kt
│       │       └── PermissionHelper.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── fragment_map.xml
│           │   ├── fragment_favorite.xml
│           │   ├── fragment_history.xml
│           │   ├── item_favorite.xml
│           │   └── item_history.xml
│           ├── menu/
│           │   └── bottom_nav_menu.xml
│           ├── navigation/
│           │   └── nav_graph.xml
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           └── drawable/
│               ├── ic_map.xml
│               ├── ic_favorite.xml
│               └── ic_history.xml
build.gradle.kts (root)
settings.gradle.kts
gradle.properties
```

---

### Task 1: 项目初始化与 Gradle 配置

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`

- [ ] **Step 1: 创建 settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MockLocation"
include(":app")
```

- [ ] **Step 2: 创建根 build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 3: 创建 gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: 创建 app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mocklocation.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mocklocation.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.amap.api:3dmap:latest.integration")
    implementation("com.amap.api:search:latest.integration")
}
```

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts
git commit -m "chore: initialize project with Gradle configuration"
```

---

### Task 2: AndroidManifest 与资源文件

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/ic_map.xml`
- Create: `app/src/main/res/drawable/ic_favorite.xml`
- Create: `app/src/main/res/drawable/ic_history.xml`
- Create: `app/src/main/res/menu/bottom_nav_menu.xml`

- [ ] **Step 1: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MockLocation">

        <meta-data
            android:name="com.amap.api.v2.apikey"
            android:value="YOUR_AMAP_API_KEY" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MockLocationService"
            android:foregroundServiceType="location"
            android:exported="false" />

    </application>
</manifest>
```

- [ ] **Step 2: 创建 strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">定位修改</string>
    <string name="tab_map">地图</string>
    <string name="tab_favorite">收藏</string>
    <string name="tab_history">历史</string>
    <string name="search_hint">搜索地点或输入坐标...</string>
    <string name="btn_start_mock">开始模拟定位</string>
    <string name="btn_stop_mock">停止模拟</string>
    <string name="btn_locate">定位</string>
    <string name="btn_reuse">再用</string>
    <string name="btn_add_favorite">添加收藏</string>
    <string name="btn_clear_all">清除全部</string>
    <string name="no_results">未找到相关位置</string>
    <string name="mock_location_not_set">请先在开发者选项中设置本应用为模拟位置应用</string>
    <string name="notification_channel_name">模拟定位</string>
    <string name="notification_mocking">正在模拟定位: %s</string>
    <string name="notification_stop">停止模拟</string>
    <string name="confirm_clear_history">确定清除所有历史记录？</string>
    <string name="title_edit_favorite">编辑收藏</string>
    <string name="title_add_favorite">添加收藏</string>
    <string name="hint_location_name">位置名称</string>
    <string name="favorite_limit_reached">收藏已达上限（50条）</string>
</resources>
```

- [ ] **Step 3: 创建 colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#FFD32F2F</color>
    <color name="on_primary">#FFFFFFFF</color>
    <color name="primary_container">#FFFFCDD2</color>
    <color name="on_primary_container">#FF9A0000</color>
    <color name="secondary">#FF616161</color>
    <color name="on_secondary">#FFFFFFFF</color>
    <color name="background">#FFFFFBFE</color>
    <color name="on_background">#FF1C1B1F</color>
    <color name="surface">#FFFFFBFE</color>
    <color name="on_surface">#FF1C1B1F</color>
    <color name="mocking_blue">#FF2196F3</color>
</resources>
```

- [ ] **Step 4: 创建 themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MockLocation" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorOnPrimary">@color/on_primary</item>
        <item name="colorPrimaryContainer">@color/primary_container</item>
        <item name="colorOnPrimaryContainer">@color/on_primary_container</item>
    </style>
</resources>
```

- [ ] **Step 5: 创建底部导航图标和菜单**

`drawable/ic_map.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z"/>
</vector>
```

`drawable/ic_favorite.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"/>
</vector>
```

`drawable/ic_history.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12.5,8v5.25l4.5,2.67 0.75,-1.23 -3.75,-2.22V8h-1.5z"/>
</vector>
```

`menu/bottom_nav_menu.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/mapFragment"
        android:icon="@drawable/ic_map"
        android:title="@string/tab_map" />
    <item android:id="@+id/favoriteFragment"
        android:icon="@drawable/ic_favorite"
        android:title="@string/tab_favorite" />
    <item android:id="@+id/historyFragment"
        android:icon="@drawable/ic_history"
        android:title="@string/tab_history" />
</menu>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/
git commit -m "feat: add AndroidManifest and resource files"
```

---

### Task 3: 数据层 - Room 实体与 DAO

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/data/db/entity/FavoriteLocation.kt`
- Create: `app/src/main/java/com/mocklocation/app/data/db/entity/LocationHistory.kt`
- Create: `app/src/main/java/com/mocklocation/app/data/db/dao/FavoriteDao.kt`
- Create: `app/src/main/java/com/mocklocation/app/data/db/dao/HistoryDao.kt`
- Create: `app/src/main/java/com/mocklocation/app/data/db/AppDatabase.kt`

- [ ] **Step 1: 创建 FavoriteLocation 实体**

```kotlin
package com.mocklocation.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 LocationHistory 实体**

```kotlin
package com.mocklocation.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class LocationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val usedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: 创建 FavoriteDao**

```kotlin
package com.mocklocation.app.data.db.dao

import androidx.room.*
import com.mocklocation.app.data.db.entity.FavoriteLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FavoriteLocation>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Insert
    suspend fun insert(location: FavoriteLocation): Long

    @Update
    suspend fun update(location: FavoriteLocation)

    @Delete
    suspend fun delete(location: FavoriteLocation)
}
```

- [ ] **Step 4: 创建 HistoryDao**

```kotlin
package com.mocklocation.app.data.db.dao

import androidx.room.*
import com.mocklocation.app.data.db.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY usedAt DESC")
    fun getAll(): Flow<List<LocationHistory>>

    @Insert
    suspend fun insert(history: LocationHistory): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY usedAt DESC LIMIT 200)")
    suspend fun trimToLimit()

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(history: LocationHistory)
}
```

- [ ] **Step 5: 创建 AppDatabase**

```kotlin
package com.mocklocation.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mocklocation.app.data.db.dao.FavoriteDao
import com.mocklocation.app.data.db.dao.HistoryDao
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory

@Database(
    entities = [FavoriteLocation::class, LocationHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mock_location_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/data/
git commit -m "feat: add Room entities, DAOs, and database"
```

---

### Task 4: 数据层 - Repository

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/data/repository/FavoriteRepository.kt`
- Create: `app/src/main/java/com/mocklocation/app/data/repository/HistoryRepository.kt`

- [ ] **Step 1: 创建 FavoriteRepository**

```kotlin
package com.mocklocation.app.data.repository

import com.mocklocation.app.data.db.dao.FavoriteDao
import com.mocklocation.app.data.db.entity.FavoriteLocation
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val dao: FavoriteDao) {
    fun getAll(): Flow<List<FavoriteLocation>> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun insert(location: FavoriteLocation): Long = dao.insert(location)

    suspend fun update(location: FavoriteLocation) = dao.update(location)

    suspend fun delete(location: FavoriteLocation) = dao.delete(location)
}
```

- [ ] **Step 2: 创建 HistoryRepository**

```kotlin
package com.mocklocation.app.data.repository

import com.mocklocation.app.data.db.dao.HistoryDao
import com.mocklocation.app.data.db.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {
    fun getAll(): Flow<List<LocationHistory>> = dao.getAll()

    suspend fun insert(history: LocationHistory): Long {
        val id = dao.insert(history)
        dao.trimToLimit()
        return id
    }

    suspend fun delete(history: LocationHistory) = dao.delete(history)

    suspend fun deleteAll() = dao.deleteAll()
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/data/repository/
git commit -m "feat: add Favorite and History repositories"
```

---

### Task 5: 工具类 - MockLocationManager 与 PermissionHelper

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/util/MockLocationManager.kt`
- Create: `app/src/main/java/com/mocklocation/app/util/PermissionHelper.kt`

- [ ] **Step 1: 创建 MockLocationManager**

```kotlin
package com.mocklocation.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

class MockLocationManager(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun isMockLocationEnabled(): Boolean {
        return try {
            val provider = LocationManager.GPS_PROVIDER
            locationManager.getProvider(provider) != null &&
                locationManager.isProviderEnabled(provider)
            val addResult = try {
                locationManager.addTestProvider(
                    provider, false, false, false, false,
                    true, true, true, 0, 1
                )
                true
            } catch (e: SecurityException) {
                false
            }
            if (addResult) {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {
                }
            }
            addResult
        } catch (e: Exception) {
            false
        }
    }

    fun startMocking(latitude: Double, longitude: Double) {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }

        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, false, false, false,
            true, true, true, 0, 1
        )
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        pushLocation(latitude, longitude)
    }

    fun pushLocation(latitude: Double, longitude: Double) {
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 0.0
            accuracy = 5.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.0f
                verticalAccuracyMeters = 5.0f
                speedAccuracyMetersPerSecond = 0.0f
            }
            speed = 0.0f
            bearing = 0.0f
        }
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
    }

    fun stopMocking() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }
    }
}
```

- [ ] **Step 2: 创建 PermissionHelper**

```kotlin
package com.mocklocation.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasLocationPermission(context: Context): Boolean {
        return locationPermissions.all {
            ContextCompat.checkSelfPermission(context, it) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestLocationPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, locationPermissions, requestCode)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openMockLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/util/
git commit -m "feat: add MockLocationManager and PermissionHelper"
```

---

### Task 6: 前台服务 - MockLocationService

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/service/MockLocationService.kt`

- [ ] **Step 1: 创建 MockLocationService**

```kotlin
package com.mocklocation.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mocklocation.app.R
import com.mocklocation.app.ui.MainActivity
import com.mocklocation.app.util.MockLocationManager
import kotlinx.coroutines.*

class MockLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.mocklocation.app.ACTION_STOP"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_NAME = "name"
    }

    private val mockManager by lazy { MockLocationManager(this) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private var currentLat = 0.0
    private var currentLng = 0.0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentLat = intent?.getDoubleExtra(EXTRA_LATITUDE, 0.0) ?: 0.0
        currentLng = intent?.getDoubleExtra(EXTRA_LONGITUDE, 0.0) ?: 0.0
        val name = intent?.getStringExtra(EXTRA_NAME) ?: ""

        val notification = buildNotification(name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startMocking()

        return START_STICKY
    }

    private fun startMocking() {
        mockManager.startMocking(currentLat, currentLng)
        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive) {
                mockManager.pushLocation(currentLat, currentLng)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        mockJob?.cancel()
        mockManager.stopMocking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(name: String): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_mocking, name))
            .setSmallIcon(R.drawable.ic_map)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_map,
                getString(R.string.notification_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/service/
git commit -m "feat: add MockLocationService with foreground notification"
```

---

### Task 7: Application 类

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/App.kt`

- [ ] **Step 1: 创建 App 类**

```kotlin
package com.mocklocation.app

import android.app.Application
import com.mocklocation.app.data.db.AppDatabase
import com.mocklocation.app.data.repository.FavoriteRepository
import com.mocklocation.app.data.repository.HistoryRepository

class App : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val favoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/App.kt
git commit -m "feat: add Application class with repository initialization"
```

---

### Task 8: 通用工具 - LocationMapper

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/ui/common/LocationMapper.kt`

- [ ] **Step 1: 创建 LocationMapper**

```kotlin
package com.mocklocation.app.ui.common

import com.amap.api.services.core.LatLonPoint
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory

data class LocationItem(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

fun FavoriteLocation.toItem() = LocationItem(id, name, address, latitude, longitude)

fun LocationHistory.toItem() = LocationItem(id, name, address, latitude, longitude)

fun LatLonPoint.toLocationItem(name: String, address: String) = LocationItem(
    id = 0,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/ui/common/
git commit -m "feat: add LocationMapper utility"
```

---

### Task 9: 布局文件

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/fragment_map.xml`
- Create: `app/src/main/res/layout/fragment_favorite.xml`
- Create: `app/src/main/res/layout/fragment_history.xml`
- Create: `app/src/main/res/layout/item_favorite.xml`
- Create: `app/src/main/res/layout/item_history.xml`
- Create: `app/src/main/res/navigation/nav_graph.xml`

- [ ] **Step 1: 创建 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host_fragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/bottom_nav"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:menu="@menu/bottom_nav_menu"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: 创建 fragment_map.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/search_card"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_margin="12dp"
        app:cardElevation="4dp"
        app:cardCornerRadius="24dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="4dp">

            <EditText
                android:id="@+id/search_input"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:hint="@string/search_hint"
                android:background="@android:color/transparent"
                android:paddingStart="16dp"
                android:paddingEnd="16dp"
                android:singleLine="true"
                android:imeOptions="actionSearch"
                android:inputType="text" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/search_results"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:maxHeight="200dp"
                android:visibility="gone" />

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

    <com.amap.api.maps.MapView
        android:id="@+id/map_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/info_card"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/info_card"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_margin="12dp"
        app:cardElevation="4dp"
        app:cardCornerRadius="12dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:id="@+id/tv_location_name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="@color/on_surface" />

            <TextView
                android:id="@+id/tv_location_coords"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="12sp"
                android:textColor="@color/secondary"
                android:layout_marginTop="4dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_mock"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:layout_marginTop="8dp"
                android:text="@string/btn_start_mock" />

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 3: 创建 fragment_favorite.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:title="@string/tab_favorite"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_favorites"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:padding="8dp"
        app:layout_constraintTop_toBottomOf="@id/toolbar"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:src="@drawable/ic_favorite"
        android:contentDescription="@string/btn_add_favorite"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 4: 创建 fragment_history.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:title="@string/tab_history"
        app:menu="@menu/history_menu"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_history"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:padding="8dp"
        app:layout_constraintTop_toBottomOf="@id/toolbar"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 5: 创建 item_favorite.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="4dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="@color/on_surface" />

            <TextView
                android:id="@+id/tv_address"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="12sp"
                android:textColor="@color/secondary"
                android:layout_marginTop="2dp" />

        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_locate"
            android:layout_width="wrap_content"
            android:layout_height="36dp"
            android:text="@string/btn_locate"
            android:textSize="12sp" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 6: 创建 item_history.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp"
    android:layout_margin="4dp">

    <TextView
        android:id="@+id/tv_icon"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="📍"
        android:textSize="18sp" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:layout_marginStart="10dp">

        <TextView
            android:id="@+id/tv_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="13sp"
            android:textColor="@color/on_surface" />

        <TextView
            android:id="@+id/tv_time"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="11sp"
            android:textColor="@color/secondary"
            android:layout_marginTop="2dp" />

    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_reuse"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:text="@string/btn_reuse"
        android:textSize="11sp" />

</LinearLayout>
```

- [ ] **Step 7: 创建 nav_graph.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/mapFragment">

    <fragment
        android:id="@+id/mapFragment"
        android:name="com.mocklocation.app.ui.map.MapFragment"
        android:label="@string/tab_map" />

    <fragment
        android:id="@+id/favoriteFragment"
        android:name="com.mocklocation.app.ui.favorite.FavoriteFragment"
        android:label="@string/tab_favorite" />

    <fragment
        android:id="@+id/historyFragment"
        android:name="com.mocklocation.app.ui.history.HistoryFragment"
        android:label="@string/tab_history" />

</navigation>
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/res/navigation/
git commit -m "feat: add all layout files and navigation graph"
```

---

### Task 10: MainActivity

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/ui/MainActivity.kt`

- [ ] **Step 1: 创建 MainActivity**

```kotlin
package com.mocklocation.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.mocklocation.app.R
import com.mocklocation.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.findNavController()
        binding.bottomNav.setupWithNavController(navController)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/ui/MainActivity.kt
git commit -m "feat: add MainActivity with bottom navigation"
```

---

### Task 11: 地图选点页 - MapViewModel 与 MapFragment

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/ui/map/MapViewModel.kt`
- Create: `app/src/main/java/com/mocklocation/app/ui/map/MapFragment.kt`

- [ ] **Step 1: 创建 MapViewModel**

```kotlin
package com.mocklocation.app.ui.map

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val selectedLat: Double = 0.0,
    val selectedLng: Double = 0.0,
    val selectedName: String = "",
    val selectedAddress: String = "",
    val isMocking: Boolean = false,
    val mockingName: String = ""
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    fun onLocationSelected(lat: Double, lng: Double, name: String, address: String) {
        _uiState.value = _uiState.value.copy(
            selectedLat = lat,
            selectedLng = lng,
            selectedName = name,
            selectedAddress = address
        )
    }

    fun startMocking() {
        val state = _uiState.value
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LATITUDE, state.selectedLat)
            putExtra(MockLocationService.EXTRA_LONGITUDE, state.selectedLng)
            putExtra(MockLocationService.EXTRA_NAME, state.selectedName)
        }
        getApplication<Application>().startForegroundService(intent)

        _uiState.value = _uiState.value.copy(
            isMocking = true,
            mockingName = state.selectedName
        )

        viewModelScope.launch {
            historyRepo.insert(
                LocationHistory(
                    name = state.selectedName,
                    address = state.selectedAddress,
                    latitude = state.selectedLat,
                    longitude = state.selectedLng
                )
            )
        }
    }

    fun stopMocking() {
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)

        _uiState.value = _uiState.value.copy(
            isMocking = false,
            mockingName = ""
        )
    }
}
```

- [ ] **Step 2: 创建 MapFragment**

```kotlin
package com.mocklocation.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.mocklocation.app.R
import com.mocklocation.app.databinding.FragmentMapBinding
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var aMap: AMap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMap(savedInstanceState)
        initSearch()
        initMockButton()
        observeState()
        checkPermissions()
    }

    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        aMap = binding.mapView.map
        aMap?.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }
    }

    private fun selectLocation(latLng: LatLng) {
        aMap?.clear()
        aMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("${latLng.latitude}, ${latLng.longitude}")
        )
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

        val geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                val address = result?.regeocodeAddress?.formatAddress ?: ""
                val name = result?.regeocodeAddress?.poiResults?.firstOrNull()?.poiName
                    ?: address
                viewModel.onLocationSelected(
                    latLng.latitude, latLng.longitude, name, address
                )
            }
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
        })

        val query = GeocodeSearch.RegeocodeQuery(
            com.amap.api.services.core.LatLonPoint(latLng.latitude, latLng.longitude),
            200f,
            GeocodeSearch.AMAP
        )
        geocodeSearch.getFromLocationAsyn(query)
    }

    private fun initSearch() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchInput.text.toString())
                true
            } else false
        }
    }

    private fun performSearch(query: String) {
        val coordRegex = Regex("""^(-?\d+\.?\d*)\s*[,，\s]\s*(-?\d+\.?\d*)$""")
        val match = coordRegex.find(query)
        if (match != null) {
            val lat = match.groupValues[1].toDouble()
            val lng = match.groupValues[2].toDouble()
            selectLocation(LatLng(lat, lng))
            return
        }

        val geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                val first = result?.geocodeAddressList?.firstOrNull()
                if (first != null) {
                    val latLng = LatLng(first.latLonPoint.latitude, first.latLonPoint.longitude)
                    selectLocation(latLng)
                } else {
                    Toast.makeText(requireContext(), R.string.no_results, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {}
        })

        val geocodeQuery = GeocodeSearch.GeocodeQuery(query, "")
        geocodeSearch.getFromLocationNameAsyn(geocodeQuery)
    }

    private fun initMockButton() {
        binding.btnMock.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.selectedLat == 0.0 && state.selectedLng == 0.0) {
                Toast.makeText(requireContext(), "请先选择一个位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (state.isMocking) {
                viewModel.stopMocking()
            } else {
                viewModel.startMocking()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.tvLocationName.text = if (state.selectedName.isNotEmpty()) {
                    "📍 ${state.selectedName}"
                } else {
                    "点击地图选择位置"
                }
                binding.tvLocationCoords.text = if (state.selectedLat != 0.0) {
                    "${state.selectedLat}°N, ${state.selectedLng}°E"
                } else {
                    ""
                }
                if (state.isMocking) {
                    binding.btnMock.text = getString(R.string.btn_stop_mock)
                } else {
                    binding.btnMock.text = getString(R.string.btn_start_mock)
                }
            }
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasLocationPermission(requireContext())) {
            PermissionHelper.requestLocationPermission(
                requireActivity(),
                REQUEST_LOCATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    companion object {
        private const val REQUEST_LOCATION = 100
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/ui/map/
git commit -m "feat: add MapFragment and MapViewModel with search and mock"
```

---

### Task 12: 收藏位置页 - FavoriteViewModel 与 FavoriteFragment

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/ui/favorite/FavoriteViewModel.kt`
- Create: `app/src/main/java/com/mocklocation/app/ui/favorite/FavoriteFragment.kt`

- [ ] **Step 1: 创建 FavoriteViewModel**

```kotlin
package com.mocklocation.app.ui.favorite

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoriteViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val favoriteRepo = app.favoriteRepository
    private val historyRepo = app.historyRepository

    val favorites = favoriteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMocking(location: FavoriteLocation) {
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LATITUDE, location.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, location.longitude)
            putExtra(MockLocationService.EXTRA_NAME, location.name)
        }
        getApplication<Application>().startForegroundService(intent)

        viewModelScope.launch {
            historyRepo.insert(
                LocationHistory(
                    name = location.name,
                    address = location.address,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            )
        }
    }

    fun addFavorite(name: String, address: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            if (favoriteRepo.count() >= 50) return@launch
            favoriteRepo.insert(
                FavoriteLocation(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }

    fun updateFavorite(location: FavoriteLocation) {
        viewModelScope.launch {
            favoriteRepo.update(location)
        }
    }

    fun deleteFavorite(location: FavoriteLocation) {
        viewModelScope.launch {
            favoriteRepo.delete(location)
        }
    }
}
```

- [ ] **Step 2: 创建 FavoriteFragment**

```kotlin
package com.mocklocation.app.ui.favorite

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mocklocation.app.R
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.databinding.FragmentFavoriteBinding
import com.mocklocation.app.databinding.ItemFavoriteBinding
import kotlinx.coroutines.launch

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoriteViewModel by viewModels()
    private val adapter = FavoriteAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = adapter

        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favorites.collect {
                adapter.submitList(it)
            }
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_favorite, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etAddress = dialogView.findViewById<EditText>(R.id.et_address)
        val etLat = dialogView.findViewById<EditText>(R.id.et_latitude)
        val etLng = dialogView.findViewById<EditText>(R.id.et_longitude)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_add_favorite)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = etName.text.toString()
                val address = etAddress.text.toString()
                val lat = etLat.text.toString().toDoubleOrNull() ?: 0.0
                val lng = etLng.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotBlank() && lat != 0.0) {
                    viewModel.addFavorite(name, address, lat, lng)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "请填写完整信息",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(location: FavoriteLocation) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_favorite, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etAddress = dialogView.findViewById<EditText>(R.id.et_address)
        val etLat = dialogView.findViewById<EditText>(R.id.et_latitude)
        val etLng = dialogView.findViewById<EditText>(R.id.et_longitude)

        etName.setText(location.name)
        etAddress.setText(location.address)
        etLat.setText(location.latitude.toString())
        etLng.setText(location.longitude.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_edit_favorite)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val updated = location.copy(
                    name = etName.text.toString(),
                    address = etAddress.text.toString(),
                    latitude = etLat.text.toString().toDoubleOrNull() ?: location.latitude,
                    longitude = etLng.text.toString().toDoubleOrNull() ?: location.longitude
                )
                viewModel.updateFavorite(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class FavoriteAdapter : RecyclerView.Adapter<FavoriteAdapter.ViewHolder>() {

        private var items: List<FavoriteLocation> = emptyList()

        fun submitList(list: List<FavoriteLocation>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFavoriteBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item.name
            holder.binding.tvAddress.text = item.address
            holder.binding.btnLocate.setOnClickListener {
                viewModel.startMocking(item)
            }
            holder.binding.root.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setItems(arrayOf("编辑", "删除")) { _, which ->
                        when (which) {
                            0 -> showEditDialog(item)
                            1 -> viewModel.deleteFavorite(item)
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemFavoriteBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
```

- [ ] **Step 3: 创建收藏编辑对话框布局 dialog_edit_favorite.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="20dp">

    <EditText
        android:id="@+id/et_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_location_name"
        android:inputType="text"
        android:layout_marginBottom="8dp" />

    <EditText
        android:id="@+id/et_address"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="地址"
        android:inputType="text"
        android:layout_marginBottom="8dp" />

    <EditText
        android:id="@+id/et_latitude"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="纬度"
        android:inputType="numberDecimal|numberSigned"
        android:layout_marginBottom="8dp" />

    <EditText
        android:id="@+id/et_longitude"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="经度"
        android:inputType="numberDecimal|numberSigned" />

</LinearLayout>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/ui/favorite/ app/src/main/res/layout/dialog_edit_favorite.xml
git commit -m "feat: add FavoriteFragment and FavoriteViewModel"
```

---

### Task 13: 历史记录页 - HistoryViewModel 与 HistoryFragment

**Files:**
- Create: `app/src/main/java/com/mocklocation/app/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/mocklocation/app/ui/history/HistoryFragment.kt`

- [ ] **Step 1: 创建 HistoryViewModel**

```kotlin
package com.mocklocation.app.ui.history

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository

    val history = historyRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMocking(item: LocationHistory) {
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LATITUDE, item.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, item.longitude)
            putExtra(MockLocationService.EXTRA_NAME, item.name)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun delete(item: LocationHistory) {
        viewModelScope.launch {
            historyRepo.delete(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepo.deleteAll()
        }
    }
}
```

- [ ] **Step 2: 创建 HistoryFragment**

```kotlin
package com.mocklocation.app.ui.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mocklocation.app.R
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.databinding.FragmentHistoryBinding
import com.mocklocation.app.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private val adapter = HistoryAdapter()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList[vh.adapterPosition]
                viewModel.delete(item)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvHistory)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_clear_all) {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirm_clear_history)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.clearAll()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            } else false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.history.collect {
                adapter.submitList(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var items: List<LocationHistory> = emptyList()
        val currentList get() = items

        fun submitList(list: List<LocationHistory>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item.name
            holder.binding.tvTime.text = dateFormat.format(Date(item.usedAt))
            holder.binding.btnReuse.setOnClickListener {
                viewModel.startMocking(item)
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemHistoryBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
```

- [ ] **Step 3: 创建历史页菜单 history_menu.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_clear_all"
        android:title="@string/btn_clear_all"
        app:showAsAction="ifRoom" />
</menu>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mocklocation/app/ui/history/ app/src/main/res/menu/history_menu.xml
git commit -m "feat: add HistoryFragment and HistoryViewModel with swipe delete"
```

---

### Task 14: Gradle Wrapper 与构建验证

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: 创建 gradle-wrapper.properties**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: 验证项目结构完整性**

Run: `find /workspace/app/src -name "*.kt" -o -name "*.xml" | sort`

Expected: 所有 Task 1-13 创建的文件均存在

- [ ] **Step 3: Commit**

```bash
git add gradle/
git commit -m "chore: add Gradle wrapper configuration"
```
