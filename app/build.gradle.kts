plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.photobooth.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.photobooth.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        renderscriptTargetApi = 26
        renderscriptSupportModeEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
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
    
    // CameraX dependencies
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")
    
    // RecyclerView for gallery
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    
    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // FFmpeg Kit for video processing (community fork - 16KB page size support)
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
    
    // ML Kit for selfie segmentation (background removal)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta5")
    
    // ML Kit Face Detection for face filters (mustaches, hats, masks)
    implementation("com.google.mlkit:face-detection:16.1.5")
}
