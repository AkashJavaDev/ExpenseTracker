import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The release signing key and its password live outside the project, in ../keys/.
val keystoreFile = rootProject.file("../keys/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storePassword").let { it != null && it != "CHANGE_ME" }

android {
    namespace = "com.expensetracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.expensetracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrinks and optimises the app; release builds are also not debuggable.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing is chosen per version below.
        }
    }

    // Same app, different starting data: see src/mine and src/share StarterData.kt.
    flavorDimensions += "version"
    productFlavors {
        create("mine") {
            dimension = "version"
            buildConfigField("boolean", "INCOME_ENABLED", "false")
            // The app on the phone was first installed debug-signed; keeping that key lets the
            // shrunk release build install over it without losing data.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("share") {
            dimension = "version"
            // Separate app id, so it can be installed next to "mine" without touching its data.
            applicationIdSuffix = ".share"
            // Bump for every shared update, so it installs over the previous one and keeps data.
            versionCode = 2
            versionName = "1.1"
            // Spent / Received tabs.
            buildConfigField("boolean", "INCOME_ENABLED", "true")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    // "mine" spending notes live in src/minePrivate, which is git-ignored. A fresh clone falls
    // back to src/minePublic (no data), so the project still builds.
    sourceSets.getByName("mine") {
        val private = file("src/minePrivate/java")
        java.srcDir(if (private.exists()) private else file("src/minePublic/java"))
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Drag-to-reorder for LazyColumn (Categories screen).
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}

// `gradlew apks` builds both versions as shrunk release builds and copies them to <project>/apk/:
// ExpenseTracker-mine.apk (this PC's debug key) and ExpenseTracker-share.apk (your release key).
tasks.register<Copy>("apks") {
    dependsOn("assembleMineRelease", "assembleShareRelease")
    doFirst {
        if (!hasReleaseKey) {
            throw GradleException("Release key not set up: fill in the passwords in ${keystoreFile.absolutePath}")
        }
    }
    from(layout.buildDirectory.file("outputs/apk/mine/release/app-mine-release.apk")) {
        rename { "ExpenseTracker-mine.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/share/release/app-share-release.apk")) {
        rename { "ExpenseTracker-share.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("apk"))
}
