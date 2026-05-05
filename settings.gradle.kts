rootProject.name = "ad-watch-app"

include(":backend")
project(":backend").projectDir = file("backend")

includeBuild("android-app")