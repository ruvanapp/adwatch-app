plugins {
    base
}

tasks.register("buildAll") {
    group = "build"
    description = "Build backend module and included Android build"
    dependsOn(":backend:build")
}
