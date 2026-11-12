plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9는 Kotlin을 내장한다. 여기서는 Kotlin 버전을 고정하는 용도로만 올리고 app에 적용하지 않는다.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
