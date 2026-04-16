# Sputnik OTK — Bootstrap (этап 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать минимальное Android-приложение «Сервис ОТК» с экраном «Привет, ОТК», собрать debug APK на удалённой Linux-машине без `sudo`, установить APK на пользовательский Huawei P30 Lite. Подтвердить рабочую цепочку: исходники → Gradle → APK → телефон.

**Architecture:** Один Activity с одной Composable-функцией, выводящей текст «Привет, ОТК» в центре экрана. Material 3 тема со светлой/тёмной палитрой по умолчанию. Иконка-заглушка (голубой квадрат с белой буквой С). Никакой бизнес-логики, NFC, сети — это будет в следующих планах.

**Tech Stack:**
- Kotlin 2.0.21
- Android Gradle Plugin 8.7.0
- Gradle 8.10.2
- Jetpack Compose BOM 2024.12.01
- Compose Compiler Gradle Plugin 2.0.21 (через `org.jetbrains.kotlin.plugin.compose`)
- Material 3 (`androidx.compose.material3`)
- AndroidX Activity Compose 1.9.3
- Android SDK API 35 (compileSdk/targetSdk), minSdk 26
- JUnit 4 для smoke-теста
- JDK 17 (Eclipse Temurin)

---

## File Structure

Создаются:

| Путь | Назначение |
|------|------------|
| `.gitignore` | Исключения git |
| `gradle/wrapper/gradle-wrapper.properties` | Версия Gradle |
| `gradle/wrapper/gradle-wrapper.jar` | Бинарь wrapper (генерируется) |
| `gradlew`, `gradlew.bat` | Скрипты wrapper (генерируются) |
| `gradle/libs.versions.toml` | Каталог версий библиотек |
| `settings.gradle.kts` | Конфиг Gradle settings |
| `build.gradle.kts` | Root build script |
| `gradle.properties` | JVM args, Android settings |
| `app/build.gradle.kts` | Build script модуля |
| `app/proguard-rules.pro` | Пустой (заглушка для release) |
| `app/src/main/AndroidManifest.xml` | Манифест приложения |
| `app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt` | Точка входа + Composable |
| `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Color.kt` | Цвета темы |
| `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Type.kt` | Типографика |
| `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Theme.kt` | Material 3 тема |
| `app/src/main/res/values/strings.xml` | Строки (название приложения) |
| `app/src/main/res/values/colors.xml` | Базовые цвета (для иконки) |
| `app/src/main/res/values/themes.xml` | XML-тема (заглушка для splashscreen) |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Голубой фон иконки |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Белая буква на иконке |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon ref |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive round icon |
| `app/src/test/kotlin/ru/sputnik/otk/SmokeTest.kt` | JVM smoke-тест (2+2=4) |
| `README.md` | Инструкции пользователю |
| `local.properties` | Путь к SDK (gitignored, генерируется) |

Не создаются в этом плане: нет сетевого кода, нет ViewModel, нет навигации, нет дополнительных экранов.

---

## Установочный preamble: тулчейн на Debian (без sudo)

### Task 1: Установить JDK 17 (Eclipse Temurin) в `~/jdk-17`

**Files:**
- Создаётся: `~/jdk-17/` (распакованная директория)
- Изменяется: `~/.bashrc` (PATH + JAVA_HOME)

- [ ] **Step 1: Скачать JDK 17 (linux-x64, Temurin)**

```bash
cd /tmp
wget -q https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz -O jdk17.tar.gz
ls -lh jdk17.tar.gz
```

Expected: файл ~190 MB.

- [ ] **Step 2: Распаковать в `~/jdk-17`**

```bash
mkdir -p ~/jdk-17
tar -xzf /tmp/jdk17.tar.gz -C ~/jdk-17 --strip-components=1
~/jdk-17/bin/java -version 2>&1
```

Expected: `openjdk version "17.0.13" ... Temurin-17.0.13+11`

- [ ] **Step 3: Прописать JAVA_HOME и PATH в `~/.bashrc`**

```bash
cat >> ~/.bashrc <<'EOF'

# JDK 17
export JAVA_HOME="$HOME/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
EOF
source ~/.bashrc
java -version 2>&1
```

Expected: `openjdk version "17.0.13" ...`.

- [ ] **Step 4: Удалить архив**

```bash
rm /tmp/jdk17.tar.gz
```

---

### Task 2: Установить Gradle 8.10.2 в `~/gradle-8.10.2`

**Files:**
- Создаётся: `~/gradle-8.10.2/`
- Изменяется: `~/.bashrc` (PATH)

- [ ] **Step 1: Скачать Gradle 8.10.2**

```bash
cd /tmp
wget -q https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -O gradle.zip
ls -lh gradle.zip
```

Expected: ~140 MB.

- [ ] **Step 2: Распаковать в `~/`**

```bash
unzip -q /tmp/gradle.zip -d ~/
ls ~/gradle-8.10.2/bin
```

Expected: `gradle  gradle.bat`.

- [ ] **Step 3: Прописать в PATH**

```bash
cat >> ~/.bashrc <<'EOF'

# Gradle 8.10.2
export PATH="$HOME/gradle-8.10.2/bin:$PATH"
EOF
source ~/.bashrc
gradle --version
```

Expected: `Gradle 8.10.2`, `JVM: 17.0.13`.

- [ ] **Step 4: Удалить архив**

```bash
rm /tmp/gradle.zip
```

---

### Task 3: Установить Android cmdline-tools в `~/Android/Sdk/cmdline-tools/latest`

**Files:**
- Создаётся: `~/Android/Sdk/cmdline-tools/latest/`
- Изменяется: `~/.bashrc` (ANDROID_HOME, PATH)

- [ ] **Step 1: Скачать cmdline-tools (Linux)**

```bash
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
ls -lh cmdline-tools.zip
```

Expected: ~150 MB.

- [ ] **Step 2: Распаковать в нужное место**

cmdline-tools распакуется в каталог `cmdline-tools/`, его нужно переименовать в `latest/`:

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
mv /tmp/cmdline-tools-extract/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
rm -rf /tmp/cmdline-tools-extract /tmp/cmdline-tools.zip
ls ~/Android/Sdk/cmdline-tools/latest/bin
```

Expected: `apkanalyzer  avdmanager  lint  retrace  screenshot2  sdkmanager`.

- [ ] **Step 3: Прописать ANDROID_HOME в `~/.bashrc`**

```bash
cat >> ~/.bashrc <<'EOF'

# Android SDK
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
source ~/.bashrc
sdkmanager --version
```

Expected: версия (например `11.0`).

---

### Task 4: Установить platform-tools, platform 35, build-tools

- [ ] **Step 1: Принять все лицензии Android SDK**

```bash
yes | sdkmanager --licenses 2>&1 | tail -3
```

Expected: `All SDK package licenses accepted.`

- [ ] **Step 2: Установить platform-tools, platform-35, build-tools 35.0.0**

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" 2>&1 | tail -5
```

Expected: `done` без ошибок. Скачается ~500 MB.

- [ ] **Step 3: Проверить установку**

```bash
ls $ANDROID_HOME/platforms/
ls $ANDROID_HOME/build-tools/
ls $ANDROID_HOME/platform-tools/adb
```

Expected:
```
android-35
35.0.0
/home/tatyana/Android/Sdk/platform-tools/adb
```

---

## Bootstrap: пустой проект

### Task 5: Создать `.gitignore`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/.gitignore`

- [ ] **Step 1: Создать `.gitignore`**

```bash
cat > /home/tatyana/workspace/sputnik-otk/.gitignore <<'EOF'
# Gradle
.gradle/
build/
local.properties

# IntelliJ / Android Studio
.idea/
*.iml
*.ipr
*.iws

# OS
.DS_Store
Thumbs.db

# APK output
*.apk

# Captures
captures/

# External native build folder
.externalNativeBuild/
.cxx/

# Keystore (никогда не коммитить)
*.jks
*.keystore
EOF
```

- [ ] **Step 2: Commit**

```bash
cd /home/tatyana/workspace/sputnik-otk
git add .gitignore
git -c user.email=cc.chanandler.bong@proton.me -c user.name=TatianaKondushova commit -m "Add .gitignore"
```

---

### Task 6: Создать `gradle/libs.versions.toml`

Каталог версий — единое место, где описаны версии всех библиотек.

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/gradle/libs.versions.toml`

- [ ] **Step 1: Создать файл**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/gradle
cat > /home/tatyana/workspace/sputnik-otk/gradle/libs.versions.toml <<'EOF'
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
junit = "4.13.2"

[libraries]
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
EOF
```

---

### Task 7: Создать `settings.gradle.kts`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/settings.gradle.kts`

- [ ] **Step 1: Создать файл**

```bash
cat > /home/tatyana/workspace/sputnik-otk/settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "sputnik-otk"
include(":app")
EOF
```

---

### Task 8: Создать корневой `build.gradle.kts`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/build.gradle.kts`

- [ ] **Step 1: Создать файл**

```bash
cat > /home/tatyana/workspace/sputnik-otk/build.gradle.kts <<'EOF'
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
EOF
```

---

### Task 9: Создать `gradle.properties`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/gradle.properties`

- [ ] **Step 1: Создать файл**

```bash
cat > /home/tatyana/workspace/sputnik-otk/gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

android.useAndroidX=true
android.nonTransitiveRClass=true

kotlin.code.style=official
EOF
```

---

### Task 10: Создать `app/build.gradle.kts`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/app/build.gradle.kts`

- [ ] **Step 1: Создать каталог и файл**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app
cat > /home/tatyana/workspace/sputnik-otk/app/build.gradle.kts <<'EOF'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.sputnik.otk"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.sputnik.otk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
EOF
```

- [ ] **Step 2: Создать пустой `proguard-rules.pro`**

```bash
touch /home/tatyana/workspace/sputnik-otk/app/proguard-rules.pro
```

---

### Task 11: Создать `AndroidManifest.xml`

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Создать манифест**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SputnikOtk">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SputnikOtk">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
EOF
```

---

### Task 12: Создать ресурсы темы и строк

**Files:**
- Создаётся: `app/src/main/res/values/strings.xml`
- Создаётся: `app/src/main/res/values/colors.xml`
- Создаётся: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Создать `strings.xml`**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main/res/values
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Сервис ОТК</string>
    <string name="hello_otk">Привет, ОТК</string>
</resources>
EOF
```

- [ ] **Step 2: Создать `colors.xml`**

```bash
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/values/colors.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="sputnik_blue">#1E88E5</color>
    <color name="white">#FFFFFFFF</color>
</resources>
EOF
```

- [ ] **Step 3: Создать `themes.xml` (XML-тема для манифеста)**

```bash
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/values/themes.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SputnikOtk" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
EOF
```

(Реальная Material 3 тема — в Compose; XML-тема нужна манифесту до запуска Compose.)

---

### Task 13: Создать иконку приложения (adaptive icon)

**Files:**
- Создаётся: `app/src/main/res/drawable/ic_launcher_background.xml`
- Создаётся: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Создаётся: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Создаётся: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: Создать фон иконки**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main/res/drawable
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/drawable/ic_launcher_background.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sputnik_blue" />
</shape>
EOF
```

- [ ] **Step 2: Создать передний план (заглушка — белая буква С)**

```bash
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/drawable/ic_launcher_foreground.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,40 m-12,0 a12,12 0 1,0 24,0 a12,12 0 1,0 -24,0 Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,40 L54,75 M40,68 L68,68"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3" />
</vector>
EOF
```

(Это упрощённая фигура «спутник со штангой». Реальная иконка будет нарисована в шаге Polish последнего плана.)

- [ ] **Step 3: Создать adaptive icon ссылки**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main/res/mipmap-anydpi-v26
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF

cat > /home/tatyana/workspace/sputnik-otk/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF
```

---

### Task 14: Создать Compose-тему

**Files:**
- Создаётся: `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Color.kt`
- Создаётся: `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Type.kt`
- Создаётся: `app/src/main/kotlin/ru/sputnik/otk/ui/theme/Theme.kt`

- [ ] **Step 1: Создать `Color.kt`**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk/ui/theme
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk/ui/theme/Color.kt <<'EOF'
package ru.sputnik.otk.ui.theme

import androidx.compose.ui.graphics.Color

val SputnikBlue = Color(0xFF1E88E5)
val SputnikBlueDark = Color(0xFF1565C0)
val SputnikBlueLight = Color(0xFF64B5F6)
EOF
```

- [ ] **Step 2: Создать `Type.kt`**

```bash
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk/ui/theme/Type.kt <<'EOF'
package ru.sputnik.otk.ui.theme

import androidx.compose.material3.Typography

val Typography = Typography()
EOF
```

(Дефолтная M3-типографика. Если понадобится — кастомизируем в плане polish.)

- [ ] **Step 3: Создать `Theme.kt`**

```bash
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk/ui/theme/Theme.kt <<'EOF'
package ru.sputnik.otk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SputnikBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SputnikBlueLight,
    secondary = SputnikBlueDark,
)

private val DarkColors = darkColorScheme(
    primary = SputnikBlueLight,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = SputnikBlueDark,
    secondary = SputnikBlue,
)

@Composable
fun SputnikOtkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
EOF
```

---

### Task 15: Создать `MainActivity` с экраном «Привет, ОТК»

**Files:**
- Создаётся: `app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt`

- [ ] **Step 1: Создать `MainActivity.kt`**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk
cat > /home/tatyana/workspace/sputnik-otk/app/src/main/kotlin/ru/sputnik/otk/MainActivity.kt <<'EOF'
package ru.sputnik.otk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SputnikOtkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HelloScreen()
                }
            }
        }
    }
}

@Composable
fun HelloScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.hello_otk),
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HelloScreenPreview() {
    SputnikOtkTheme {
        HelloScreen()
    }
}
EOF
```

---

### Task 16: Создать smoke-тест (JVM)

**Files:**
- Создаётся: `app/src/test/kotlin/ru/sputnik/otk/SmokeTest.kt`

- [ ] **Step 1: Написать падающий тест-якорь**

```bash
mkdir -p /home/tatyana/workspace/sputnik-otk/app/src/test/kotlin/ru/sputnik/otk
cat > /home/tatyana/workspace/sputnik-otk/app/src/test/kotlin/ru/sputnik/otk/SmokeTest.kt <<'EOF'
package ru.sputnik.otk

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun `arithmetic still works`() {
        assertEquals(4, 2 + 2)
    }
}
EOF
```

(Smoke-тест нужен исключительно для проверки, что test toolchain собирается и запускается. Реальные тесты придут с бизнес-логикой в следующих планах.)

---

### Task 17: Сгенерировать Gradle wrapper

**Files:**
- Создаётся: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Запустить `gradle wrapper`**

```bash
cd /home/tatyana/workspace/sputnik-otk
gradle wrapper --gradle-version 8.10.2 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL`. В каталоге появятся `gradlew`, `gradlew.bat`, `gradle/wrapper/`.

- [ ] **Step 2: Сделать `gradlew` исполняемым**

```bash
chmod +x /home/tatyana/workspace/sputnik-otk/gradlew
ls -l /home/tatyana/workspace/sputnik-otk/gradlew
```

Expected: `-rwxrwxr-x`.

---

### Task 18: Первая сборка debug APK

**Files:**
- Создаётся (автоматически): `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Запустить сборку**

```bash
cd /home/tatyana/workspace/sputnik-otk
./gradlew assembleDebug --warning-mode=summary 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL in <время>`. Никаких `FAILURE`. Время первой сборки 3-10 минут (скачивает все зависимости).

- [ ] **Step 2: Проверить, что APK собран**

```bash
ls -lh /home/tatyana/workspace/sputnik-otk/app/build/outputs/apk/debug/app-debug.apk
```

Expected: файл ~5-7 MB.

- [ ] **Step 3: Запустить smoke-тест**

```bash
cd /home/tatyana/workspace/sputnik-otk
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, `SmokeTest > arithmetic still works PASSED`.

- [ ] **Step 4: Запустить lint (по желанию, не блокирует)**

```bash
./gradlew :app:lintDebug 2>&1 | tail -5
```

Expected: либо `BUILD SUCCESSFUL`, либо предупреждения в `app/build/reports/lint-results-debug.html`. Не критично для bootstrap-плана.

---

### Task 19: Закоммитить весь bootstrap

- [ ] **Step 1: Проверить статус**

```bash
cd /home/tatyana/workspace/sputnik-otk
git status --short
```

Expected: новые файлы (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `app/`, `gradlew`, и т.д.).

- [ ] **Step 2: Добавить и закоммитить**

```bash
git add .
git -c user.email=cc.chanandler.bong@proton.me -c user.name=TatianaKondushova commit -m "$(cat <<'EOF'
Bootstrap empty Android project

- Kotlin 2.0.21 + AGP 8.7.0 + Gradle 8.10.2
- Jetpack Compose with Material 3 (Compose BOM 2024.12.01)
- Single MainActivity displaying "Привет, ОТК"
- Adaptive launcher icon (blue background, white satellite shape)
- JUnit smoke test confirming JVM test toolchain works
- minSdk 26, compile/targetSdk 35

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
git log --oneline | head -5
```

Expected: новый коммит виден в логе.

---

### Task 20: Создать `README.md` с инструкциями

**Files:**
- Создаётся: `/home/tatyana/workspace/sputnik-otk/README.md`

- [ ] **Step 1: Написать README**

```bash
cat > /home/tatyana/workspace/sputnik-otk/README.md <<'EOF'
# Сервис ОТК

Android-приложение для контроллёров ОТК. Записывает результаты проверок в Google Sheets через Apps Script webhook.

Подробная спецификация: [docs/superpowers/specs/2026-04-16-sputnik-otk-design.md](docs/superpowers/specs/2026-04-16-sputnik-otk-design.md).

## Сборка

Требования: JDK 17, Android SDK с platform-35.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Тесты:

```bash
./gradlew test
```

## Установка APK на телефон

1. Скачать `app-debug.apk` на телефон (через USB / мессенджер / облачный диск).
2. На телефоне: «Настройки» → «Безопасность» → разрешить «Установка из неизвестных источников» для приложения, через которое будете открывать APK.
3. Открыть APK на телефоне → «Установить».
4. На рабочем столе появится иконка «Сервис ОТК» → запустить.

## Структура проекта

- `app/src/main/kotlin/ru/sputnik/otk/` — исходный код Kotlin.
- `app/src/main/res/` — ресурсы Android (строки, цвета, иконка).
- `app/src/test/kotlin/` — JVM unit-тесты.
- `docs/superpowers/specs/` — спецификации.
- `docs/superpowers/plans/` — планы реализации по этапам.
EOF
```

- [ ] **Step 2: Закоммитить README**

```bash
cd /home/tatyana/workspace/sputnik-otk
git add README.md
git -c user.email=cc.chanandler.bong@proton.me -c user.name=TatianaKondushova commit -m "Add README with build and install instructions"
```

---

## Раздача APK пользователю

### Task 21: Опубликовать APK для скачивания пользователем

Из удалённой Linux-машины пользователь не может напрямую забрать файл. Варианты передачи (выбрать один; первый — рекомендуемый):

- [ ] **Step 1: Показать путь к APK и его размер**

```bash
ls -lh /home/tatyana/workspace/sputnik-otk/app/build/outputs/apk/debug/app-debug.apk
sha256sum /home/tatyana/workspace/sputnik-otk/app/build/outputs/apk/debug/app-debug.apk
```

Expected: размер ~5-7 MB + хеш для проверки.

- [ ] **Step 2: Передать APK пользователю**

Один из вариантов (исполнителю выбрать один):

**Вариант A — GitHub Release:** push репозиторий на GitHub (`TatianaKondushova/sputnik-otk` приватный), создать release `v0.1.0-bootstrap`, прикрепить APK. Пользователь скачивает по ссылке с телефона.

**Вариант B — webhook на временный загрузчик:** отдельная задача, пока не делаем.

**Вариант C — попросить пользователя забрать через клиент Claude Code** (если интерфейс это поддерживает) — описать путь к файлу выше, попросить пользователя его скачать.

- [ ] **Step 3: Дождаться, пока пользователь установит APK на свой Huawei P30 Lite**

Критерии приёмки от пользователя:
- [ ] Иконка «Сервис ОТК» с голубым фоном появилась на рабочем столе.
- [ ] Тап по иконке открывает белый экран с текстом «Привет, ОТК» по центру.
- [ ] Старое приложение `com.example.my_application` (SK-Warranty) **не пропало** и продолжает запускаться.

Если что-то не работает — собрать `./gradlew assembleDebug --stacktrace`, проверить `adb logcat` (подключив телефон по USB к Windows-машине пользователя через Android Studio), починить.

---

## Setup GitHub-репозитория (опционально, можно отложить)

### Task 22: Создать приватный репозиторий на GitHub и запушить

- [ ] **Step 1: Создать репозиторий через `gh` CLI**

(Из памяти известно: `gh` CLI авторизован у пользователя.)

```bash
cd /home/tatyana/workspace/sputnik-otk
gh repo create TatianaKondushova/sputnik-otk --private --source=. --remote=origin --description "Android app for OTK quality control"
```

Expected: `✓ Created repository TatianaKondushova/sputnik-otk on GitHub`.

- [ ] **Step 2: Запушить ветку main**

```bash
git push -u origin main
```

Expected: `Branch 'main' set up to track 'origin/main'`.

- [ ] **Step 3: Проверить, что репо открывается в браузере**

```bash
gh repo view --web
```

(Покажет ссылку, по которой пользователь может открыть репо.)

---

## Self-review (после завершения всех task)

- [ ] Бывает ли непосредственная сборка `./gradlew assembleDebug` после клонирования repo с нуля? Проверить: `git clone <repo> /tmp/test && cd /tmp/test && ./gradlew assembleDebug`.
- [ ] APK открывается на пользовательском Huawei и показывает «Привет, ОТК»? (Подтверждение от пользователя.)
- [ ] Старый APK не пострадал? (Подтверждение от пользователя.)
- [ ] Smoke-тест проходит: `./gradlew test`?

Если все 4 пункта галочки → bootstrap-этап завершён, можно писать план №2 (HomeScreen).

---

## Что НЕ в этом плане (отложено)

- Реальный Material 3 splashscreen — добавим в polish-плане.
- Подпись release APK — последний план (polish).
- ProGuard/R8 правила — последний план.
- Любой UI кроме одного экрана с текстом — следующие планы.
- Любая сетевая логика — план 3.
- NFC, ViewModel, репозитории — планы 3-7.
