plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvm()

    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    testFixturesImplementation(libs.ktor.client.mock)
    testFixturesImplementation(libs.kotlinx.serialization.json)
}
