# Project-specific R8/ProGuard rules for the matchday release build.
#
# Nothing here yet: Compose, kotlinx.serialization, and the Light SDK's own
# entry-point classes (LightActivity, LightSdkApplication, LightSdkReceiver,
# etc.) all ship their own consumer proguard rules bundled in their
# dependencies (see sdk/client/consumer-rules.pro and
# sdk/ui/consumer-rules.pro for the SDK side), which R8 picks up
# automatically — this app doesn't do anything reflection-based of its own
# (SoccerModels.kt's @Serializable classes use the kotlinx.serialization
# compiler plugin, not runtime reflection).
#
# If a release build crashes with a ClassNotFoundException/NoSuchMethodError
# that a debug build doesn't, that's R8 having stripped or renamed something
# it shouldn't have — add a -keep rule for that specific class here rather
# than turning minification off.
