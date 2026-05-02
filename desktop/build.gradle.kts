plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared-core"))
    implementation(compose.desktop.currentOs)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "dev.conduit.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Conduit MC"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/Conduit.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/Conduit.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/logo-icon.png"))
            }
        }
    }
}
