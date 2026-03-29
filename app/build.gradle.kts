plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.stormgamesstudios.stormstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stormgamesstudios.stormstore"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    
    // Iconos extendidos para usar Icons.Default.Archive
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Tarea para sincronizar el versionName con el archivo version.txt en la raíz
tasks.register("syncVersionFile") {
    group = "versioning"
    description = "Copia el versionName actual al archivo version.txt de la raíz."
    
    doLast {
        val versionFile = file("${project.rootDir}/version.txt")
        val currentVersion = android.defaultConfig.versionName ?: "1.0.0"
        versionFile.writeText(currentVersion)
        println("Archivo version.txt actualizado a v$currentVersion")
    }
}

// Hacer que se ejecute automáticamente antes de construir
tasks.named("preBuild") {
    dependsOn("syncVersionFile")
}
