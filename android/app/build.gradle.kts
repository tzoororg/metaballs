plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.tzoororg.metaballs"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.tzoororg.metaballs"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}

// Single source of truth for the animation stays at the repo root (../../index.html);
// this just copies it into assets/ before the build packages them.
// ponytail: no incremental/watch wiring — Copy task re-runs each build, cheap for one file.
tasks.register<Copy>("copyIndexHtml") {
    from(rootProject.projectDir.parentFile.resolve("index.html"))
    from(rootProject.projectDir.parentFile.resolve("evolve.html"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn("copyIndexHtml")
}
