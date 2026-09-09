# Light SDK demo tools

This fork trims the base Light SDK's demo modules down to just the one tool it actually ships —
see the root README for why. The others (`ui-demo`, `weather`, `authenticator`, `audio-demo`) that
ship in [Light's own upstream SDK](https://github.com/lightphone/light-sdk) aren't included here.

| Module | Package | Description |
|--------|---------|-------------|
| `matchday` | `com.thelightphone.soccerfootball` | scores, fixtures, standings, and a "My Team" view via API-Football — see [its own README](matchday/README.md)

## How to run on device

```bash
./gradlew :examples:matchday:installDebug
adb shell am start -n com.thelightphone.soccerfootball/com.thelightphone.sdk.LightActivity
```
