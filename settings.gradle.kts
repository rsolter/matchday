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
// sdk:server and sdk:emulator are kept (unlike the trimmed examples/tool modules below) because
// they power the LightOS-emulator dev/test loop documented in docs/system_app, which is still in
// active use for this tool even though soccer-football's own build.gradle.kts doesn't depend on
// either directly (verified: :sdk:client only depends on :sdk:shared, :sdk:ui, :lint-rules).
include(":sdk:server")
include(":sdk:emulator")
// The placeholder ":tool" scaffold and the other three demo apps that ship with the base Light SDK
// fork (ui-demo, weather, authenticator, audio-demo) were removed — this fork exists to build and
// ship soccer-football only, and none of them were referenced anywhere in that module's build graph.
// Matches the trim-to-just-your-tool pattern other community forks on this SDK use (e.g. light-mail).
include(":examples:soccer-football")
project(":examples:soccer-football").projectDir = file("examples/soccer-football")
