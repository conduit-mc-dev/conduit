rootProject.name = "conduit-mc"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":shared-core")
include(":daemon")
include(":desktop")
include(":web")
