pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AdWatchApp"

include(":app")
include(":core-ui")
include(":core-network")
include(":core-storage")
include(":feature-auth")
include(":feature-ads")
include(":feature-wallet")
include(":feature-cashout")
include(":feature-home")
include(":feature-trust")
