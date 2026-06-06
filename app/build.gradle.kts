import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
}

// Load keystore properties for production signing
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.bharath.carcrashdetection"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bharath.carcrashdetection"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Enable 16 KB page size compatibility
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        
        // Explicit 16 KB page size compatibility
        manifestPlaceholders["android:extractNativeLibs"] = "false"
        
        // Additional build config for 16 KB compatibility
        buildConfigField("boolean", "ENABLE_16KB_PAGE_SIZE", "true")
        
        // Force AndroidX compatibility
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty() && keystoreProperties["storeFile"] != null) {
                val keystoreFile = file(keystoreProperties["storeFile"] as String)
                if (keystoreFile.exists()) {
                    keyAlias = keystoreProperties["keyAlias"] as String?
                    keyPassword = keystoreProperties["keyPassword"] as String?
                    storeFile = keystoreFile
                    storePassword = keystoreProperties["storePassword"] as String?
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only use release signing if keystore exists
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production optimizations
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "false")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "true")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug features
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "false")
        }
    }
    
    // Configure native library handling for 16 KB page size compatibility
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // Exclude problematic native libraries that don't support 16 KB page sizes
            excludes += listOf(
                "**/libimage_processing_util_jni.so"
            )
        }
        // Additional packaging options for 16 KB compatibility
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
    
    // Disable native library extraction for 16 KB page size compatibility
    androidResources {
        noCompress += listOf("so")
    }
    
    // Additional configuration for 16 KB page size compatibility
    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = false
        }
        abi {
            enableSplit = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    
    // Force AndroidX compatibility
    configurations.all {
        resolutionStrategy {
            force("androidx.core:core:1.12.0")
            force("androidx.appcompat:appcompat:1.6.1")
            force("androidx.fragment:fragment:1.6.2")
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // SharedPreferences
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // MQTT - Eclipse Paho with AndroidX compatibility fixes
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    // LocalBroadcastManager replacement - ensure this is available
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // Additional AndroidX support libraries that might be needed
    implementation("androidx.annotation:annotation:1.7.1")
    // Force AndroidX compatibility
    implementation("androidx.core:core:1.12.0")
    // Additional AndroidX support
    implementation("androidx.fragment:fragment:1.6.2")
    
    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Lottie for animations
    implementation("com.airbnb.android:lottie:6.4.0")
    
    // Bluetooth and WiFi Direct for ESP32 integration
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Location services for GPS integration
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // Camera and image processing for medical profile photos
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
}