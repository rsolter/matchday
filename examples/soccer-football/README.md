# Soccer Pro

A Light Phone III tool sourced from [API-Football](https://www.api-football.com) (api-football.com,
`v3.football.api-sports.io`) — Premier League, Serie A, UEFA Champions League, and UEFA Europa
League, with Settings, Scores, Fixtures, Standings, and a "My Team" screen (league position,
upcoming/recent fixtures, and who's unavailable for the next match).

This module landed on API-Football after evaluating a few other free/unofficial data sources
(football-data.org, ESPN's unofficial site API, FotMob's unofficial API) that either had tighter
restrictions or weren't worth the ongoing maintenance cost of an undocumented API. Unlike those,
API-Football is a documented, official, stable REST API — but it has real free-tier restrictions
of its own, described below.

This is meant to be a tool the broader LightOS community can install and use for free — see
"Phase 1 vs. production" below for the plan to get it off free-tier historical data and onto a
shared caching proxy so a paid plan's request budget is spent once, server-side, rather than per
installed phone.

## Data source

Base URL `https://v3.football.api-sports.io`, authenticated via an `x-apisports-key` header (get a
key at api-football.com; a free tier exists, see below). Every endpoint and field this tool relies
on was verified against real responses from a real key rather than assumed from documentation —
API-Football's docs are accurate as far as they go, but the free-tier restrictions below are not
documented anywhere and were only found by testing.

**Free-tier restrictions, confirmed empirically:**

- **Seasons are capped to 2022-2024.** `GET /leagues?id=39&season=2026` returns
  `{"errors":{"plan":"Free plans do not have access to this season, try from 2022 to 2024."}}`.
  There is no way to get genuinely live, current-season data on this tier.
- **The `last` query parameter is Pro-only.** `GET /fixtures?league=39&season=2023&last=5` returns
  `{"errors":{"plan":"Free plans do not have access to the Last parameter."}}`. This build uses
  `from`/`to` date-range parameters instead, confirmed working on the free tier.
- **Errors come back as HTTP 200, not a non-2xx status.** Both restrictions above, and any other
  plan-related error, arrive as a normal 200 response with a populated `errors` field — a real
  *success* response carries `"errors":[]` (empty array), a real *failure* carries `"errors":{...}`
  (a non-empty object). `ApiFootballApi.getChecked` in `SoccerApi.kt` checks this explicitly, so a
  plan restriction surfaces as a real message instead of silently decoding into an empty list and
  looking like "no matches today."

**Endpoints used, each confirmed against a real response this session** (see `SoccerModels.kt`'s
per-endpoint doc comments for the full shape and gotchas found in each):

- `GET /fixtures` (by `league`+`season`+`from`/`to`, or `team`+`season`+`from`/`to`) — Scores and
  Fixtures. The `team`-scoped variant (used by My Team) was **not** independently curl-verified —
  only the `league`-scoped one was actually tested.
- `GET /fixtures/events` — the match detail screen's Event Timeline tab. `subst` events flip
  `player`/`assist` to mean player-off/player-on rather than scorer/assist.
- `GET /fixtures/statistics` — the Stats tab. `value` is genuinely heterogeneous: int, a percentage
  string ("66%"), a decimal string ("2.08" for expected_goals), or `null` (confirmed meaning zero).
- `GET /fixtures/lineups` — the Home/Away Lineup tabs. Includes a real per-player `grid`
  ("row:col") pitch position and a `formation` label directly from the API — no client-side
  inference needed here, unlike the ESPN variant of this tool.
- `GET /standings` — flat table for domestic leagues (confirmed: Premier League, 20 teams in one
  array), split into multiple group arrays for UEFA competitions (confirmed: Champions League,
  8 groups of 4 in `standings: [[...], [...], ...]`).
- `GET /injuries` (by `fixture`, or by `league`+`season`+`team`) — My Team's "Unavailable" section.
  Two gotchas: it's a season-long log (one row per fixture a player missed), not a "current state"
  snapshot, so this build calls the fixture-scoped variant against one specific reference fixture
  rather than pulling and filtering the whole season; and despite the name, `reason` includes both
  real injuries and suspension causes ("Red Card", "Yellow Cards") — split into Injured/Suspended
  buckets rather than shown as one undifferentiated "injuries" list.

**Not independently verified — worth a quick real check before relying on them:**

- League IDs 135 (Serie A) and 3 (UEFA Europa League) — 39 (Premier League) and 2 (Champions
  League) were confirmed, 135 and 3 are API-Football's commonly published IDs but weren't
  curl-tested this session.
- The `team`+`season`+`from`/`to` fixtures query (My Team's fixture list) — only the
  `league`-scoped equivalent was tested.
- Every `MatchStatus` mapping other than `"FT"` — the free tier's 2022-2024 window made it hard to
  catch a genuinely live or not-yet-started match; the rest of the status-code table comes from
  API-Football's documentation, not a real response.

## Phase 1 vs. production

This build is **Phase 1** of a 3-phase plan agreed on before writing any code: (1) build the full
visual/UI/data-flow shape against the free tier's historical 2022-2024 data, (2) design a
lazy/TTL-based caching proxy server so a paid plan's request budget is shared server-side instead
of spent per-installed-phone, (3) point this app at that proxy with a paid plan for real
current-season, live data.

Two deliberate Phase-1-only compromises, both called out inline in code:

- **`PHASE1_SEASON`** (`SoccerModels.kt`) pins every request to the 2023/24 season, since the free
  tier can't see anything newer.
- **`phase1Today()`** (`SoccerFormatting.kt`) stands in for "today" everywhere fixture/season logic
  needs it — August 19, 2023, a real confirmed Premier League matchday — since the device's real
  current date has no matches on this tier. `todayLocalDate()` still reads the real clock for
  things that are genuinely about *now* (e.g. "last updated 2 minutes ago").

Grep for `PHASE1_SEASON` and `phase1Today` when doing the Phase 3 swap.

**There is deliberately no auto-refresh/poll loop in this build**, unlike the ESPN/football-data.org
variants of this tool. The free tier is capped at 100 requests/day total — a 60-second poll across
even 2-3 followed leagues would blow through that in well under an hour. Phase 1's data is also a
frozen historical season, so polling for "updates" would be spending quota on data that literally
cannot change. Refresh here is on-demand only: once on first load, and via a "Refresh now" row in
Settings — which also happens to be this build's answer to the requested "no visible refresh
button" feature (see below). Phase 3, with the request cost absorbed by the caching proxy instead
of each phone, is where a real poll loop belongs.

## What it does

- **Scores** (default view): [`phase1Today()`](#phase-1-vs-production)'s matches, grouped by
  competition. No auto-refresh (see above) — pull-to-date is via Settings' "Refresh now".
- **Settings**: which of the four leagues you follow, your API key (masked, changeable, clearable),
  your My Team pick (changeable, clearable), and the manual refresh action.
- **Fixtures**: pick a followed league, see its matches ±10/+21 days around `phase1Today()`,
  grouped by date, auto-scrolled to today.
- **Standings**: pick a followed league, see its table — grouped by "Group A"/"Group B"/etc.
  automatically for UEFA competitions, one flat table otherwise (see the Data source section).
- **My Team**: reachable via the star icon in the bottom bar. First use walks through a two-step
  setup (pick a followed league, then a team from that league's standings — there's no team-search
  endpoint verified for this build, so the team list comes from data already on screen). Once set,
  shows league position, a handful of upcoming and recent fixtures, and who's unavailable
  (injured/suspended, split) for the team's next match.
- **Match detail**: tap any match row from Scores, Fixtures, or My Team. Four tabs — **Stats**,
  **Timeline**, **Home Lineup**, **Away Lineup**:
  - Stats: raw API-Football stat types, lightly reformatted (underscores → spaces, title case) but
    not curated into a fixed order — see the "not independently verified" note above on why.
  - Timeline: a flat, chronological, text-only feed of goals, substitutions, cards, and VAR
    reviews — the feature that prompted this whole build ("a text based event timeline would be a
    good thing to add").
  - Home/Away Lineup: starting XI laid out by real pitch row (from API-Football's own `grid`
    field, not inferred), plus formation label, coach, and substitutes.

Bottom bar order, left to right: **Settings, My Team, Standings, Fixtures.** Deliberately no
Refresh icon (see above).

## Building it

Standard Light SDK tool module layout — `build.gradle.kts`/`lighttool.toml` plus a
screen/viewmodel/DataStore architecture. It's already wired into the root `settings.gradle.kts`.

```bash
./gradlew :examples:soccer-football:installDebug
adb shell am start -n com.thelightphone.soccerfootball/com.thelightphone.sdk.LightActivity
```

`lighttool.toml` defaults `serverPackage` to the LightOS emulator (`com.thelightphone.sdk.emulator`)
— see [`docs/system_app`](../../docs/system_app) for setting that up in Android Studio. Switch it
to `com.lightos` before sideloading to a real Light Phone III.

You'll need a real API-Football key to get past the first screen — see Settings' "learn more" link
(or the Attribution screen) for how to get a free one.

**A word on that key**: don't paste it in plaintext anywhere it might get logged or shared (this
one included earlier in this build's own development — it should be rotated from the API-Football
dashboard if it hasn't been already). If you're testing via `curl` from a terminal, `export
API_KEY=...` once and reference `$API_KEY` in commands rather than typing the literal key each
time.

## Honesty check

This was built by adapting the ESPN/football-data.org variants' already-tested screen/viewmodel
structure to a new, independently-verified API-Football data layer — every DTO shape in
`SoccerModels.kt` traces back to a real `curl` response gathered and reviewed before any Kotlin was
written, not assumed from API-Football's documentation. That said, this is still, like the rest of
this repo, a sandbox build with no Android SDK: it wasn't compiled locally before being handed off.
A careful manual read-through (imports, types, sealed-class exhaustiveness, brace/paren balance —
checked programmatically across all six source files, all balanced) stood in for a real build.
Treat your first `./gradlew :examples:soccer-football:installDebug` as the actual first test —
please report back anything that doesn't compile, along with the three specific "not independently
verified" items above (Serie A/Europa League IDs, the team-scoped fixtures query, and every
`MatchStatus` code besides `FT`) if you hit any of them.
