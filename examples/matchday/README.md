# Matchday

A Light Phone III tool for live soccer scores, fixtures, and standings, sourced from
[API-Football](https://www.api-football.com) — 22 competitions across England (Premier League,
Championship, FA Cup, EFL Cup), Italy (Serie A, Coppa Italia), Spain (La Liga, Copa del Rey),
Germany (Bundesliga, DFB-Pokal), France (Ligue 1, Coupe de France), the Netherlands (Eredivisie),
Portugal (Primeira Liga), Turkey (Süper Lig), the United States (MLS), Mexico (Liga MX), Europe
(UEFA Champions League, Europa League, Conference League), and North America (CONCACAF Champions
Cup, Leagues Cup).

## What it does

- **Scores** (default view): matches from two weeks back to four weeks ahead across every
  competition you follow, grouped by day and competition.
- **Competitions** (trophy icon): pick a followed league, see its table — split into groups
  (e.g. MLS's Eastern/Western conferences) where the competition has them, one flat table
  otherwise. Knockout cups have no table and are left out of this list.
- **My Team**: pick a league and a team once; after that, see its league position, a handful of
  upcoming and recent fixtures, and who's unavailable (injured or suspended, split out) for the
  next match.
- **Match detail**: tap any match for four tabs — Stats, Timeline (a flat, chronological,
  text-only feed of goals, substitutions, cards, and VAR reviews), and Home/Away Lineup (starting
  XI by real pitch position, formation, coach, and substitutes).
- **Player**: tap any player in a lineup for their season — Summary (appearances, goals,
  minutes, rating, per competition), Stats (totals and per 90), Matches (the season's match list),
  and Career (clubs and national teams). Season totals cover leagues and European competitions;
  the match list includes domestic cups too. Stats update nightly.
- **Settings**: which competitions you follow and your My Team pick. Scores refreshes from the
  bottom bar's Refresh icon — there's no auto-refresh/poll loop, by design.

Bottom bar order, left to right: **Settings, My Team, Competitions, Refresh.**

## Data source

The app talks to a caching proxy (`soccer-pro-proxy`, deployed separately) that holds a paid
API-Football key server-side, so a single request budget is shared across every installed phone
rather than each one needing its own key. No client-side API key or setup is needed — the app
shows real, current data on first launch.

Team crests, league badges, and player headshots come from the proxy too (its `/img/` routes, which
serve stored copies of API-Football's images), and each one is kept on the phone after its first
download — so they load instantly on later screens and still show up offline.

## Building it

Standard Light SDK tool module layout — `build.gradle.kts`/`lighttool.toml` plus a
screen/viewmodel/DataStore architecture. It's already wired into the root `settings.gradle.kts`.

```bash
./gradlew :examples:matchday:installDebug
adb shell am start -n com.thelightphone.soccerfootball/com.thelightphone.sdk.LightActivity
```

`lighttool.toml` defaults `serverPackage` to the LightOS emulator (`com.thelightphone.sdk.emulator`)
— see [`docs/system_app`](../../docs/system_app) for setting that up in Android Studio. Switch it
to `com.lightos` before sideloading to a real Light Phone III.

### Building a production (release) APK to share

By default `assembleRelease` signs with the same shared dev keystore as `debug`
(`sdk/keys/lightsdk-dev.jks`) — fine for local testing, but its password ("android") is public in
this repo, so anyone can resign an APK with that same key. For a build meant to be handed out as an
authentic release, generate your own private key first:

```bash
cd examples/matchday
keytool -genkeypair -v -keystore matchday-release.jks -alias matchday-release \
    -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` prompts for the store/key passwords and your name/org info interactively — nothing here
needs typing into a chat or a script. Then copy `keystore.properties.example` to
`keystore.properties` in this same directory and fill in the passwords you just chose. Both
`matchday-release.jks` and `keystore.properties` are gitignored — they stay on your machine only.

Once that's in place, flip `serverPackage` in `lighttool.toml` to `com.lightos` (see above), then:

```bash
./gradlew :examples:matchday:assembleRelease
```

→ `examples/matchday/build/outputs/apk/release/matchday-release.apk`, signed with your
private key. Note this makes it installable (via `adb install` or LightOS's "Any tools" sideload
option, which warns the user it isn't Light-verified) — it does not make it a "Light-approved" or
"SDK-built" tool in LightOS's own sense, which per this SDK's own docs requires Light to build and
sign it themselves from a public git commit.

## Known limitations

- The Stats tab shows raw API-Football stat types, lightly reformatted (underscores → spaces,
  title case) but not curated into a fixed display order.
