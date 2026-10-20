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
// sdk:server and sdk:emulator are kept (unlike the demo apps trimmed below) because
// they power the LightOS-emulator dev/test loop documented in docs/system_app, which is still in
// active use for this tool even though matchday's own build.gradle.kts doesn't depend on
// either directly (verified: :sdk:client only depends on :sdk:shared, :sdk:ui, :lint-rules).
include(":sdk:server")
include(":sdk:emulator")
// The demo apps that ship with the base Light SDK (ui-demo, weather, authenticator, audio-demo)
// were removed — this fork exists to build and ship Matchday only. Matchday itself lives in the
// SDK's standard :tool module, in place of its placeholder scaffold: tool/ is the only path Light's
// build server reads a tool from (tool/lighttool.toml, tool/build.gradle.kts, tool/src/main/**; see
// builder/README.md). It lived in examples/matchday before 1.6.5, which the tool library's
// submission check couldn't find.
include(":tool")
