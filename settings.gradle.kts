pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "light-sdk"

includeBuild("plugin")
include(":lint-rules")
include(":sdk:shared")
include(":sdk:ui")
include(":sdk:client")
// The demo apps that ship with the base Light SDK (ui-demo, weather, authenticator, audio-demo)
// were removed — this fork exists to build and ship Matchday only. Matchday itself lives in the
// SDK's standard :tool module, in place of its placeholder scaffold: tool/ is the only path Light's
// build server reads a tool from (tool/lighttool.toml, tool/build.gradle.kts, tool/src/main/**; see
// builder/README.md). It lived in examples/matchday before 1.6.5, which the tool library's
// submission check couldn't find.
include(":tool")

// The tool builder passes -DlightSdk.toolOnly=true to configure only what :tool needs — same switch
// as upstream's settings.gradle.kts. sdk:server and sdk:emulator are kept for the LightOS-emulator
// dev/test loop documented in docs/system_app; Matchday doesn't depend on either.
if (providers.systemProperty("lightSdk.toolOnly").orNull != "true") {
    include(":sdk:server")
    include(":sdk:emulator")
}
