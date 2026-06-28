import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val appVersionName = "0.0.1"
val appVersionCode = appVersionName.split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "com.transcriptai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.transcriptai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Default to the deployed backend via CloudFront (HTTPS + IPv6, so it connects on IPv6-only
        // mobile networks). Override for the emulator in local.properties (http://10.0.2.2:8010/api/).
        val apiBaseUrl = localProperties.getProperty("apiBaseUrl", "https://d2nrls693zgomu.cloudfront.net/api/")
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$apiBaseUrl\"")
        // Same Google OAuth web client id as the field-repo (Credential Manager serverClientId).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"614092441670-3e5k15srupq9mfpg3aktqfkjvkavu0g3.apps.googleusercontent.com\"")

        // Microsoft (Azure AD) + Yahoo OAuth. Set the client IDs in local.properties once the apps are
        // registered; the redirect URI below must be registered with each provider verbatim.
        val microsoftClientId = localProperties.getProperty("microsoftClientId", "")
        val yahooClientId = localProperties.getProperty("yahooClientId", "")
        val oauthRedirectUri = localProperties.getProperty("oauthRedirectUri", "com.transcriptai.app://oauth")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        buildConfigField("String", "YAHOO_CLIENT_ID", "\"$yahooClientId\"")
        buildConfigField("String", "OAUTH_REDIRECT_URI", "\"$oauthRedirectUri\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    // Google Workspace authorization (Calendar / Tasks access token, separate from sign-in).
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Background upload + processing-status polling.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // In-app audio playback.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Push (FCM). Compiles without google-services.json; to actually receive push, drop
    // google-services.json into app/ and apply the com.google.gms.google-services plugin.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
