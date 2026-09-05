# Soccer Pro

A Light Phone III tool sourced from [API-Football](https://www.api-football.com) (api-football.com,
`v3.football.api-sports.io`) — 15 competitions across England (Premier League, Championship, FA Cup,
EFL Cup), Italy (Serie A, Coppa Italia), Spain (La Liga, Copa del Rey), Germany (Bundesliga,
DFB-Pokal), France (Ligue 1, Coupe de France), Europe (UEFA Champions League, UEFA Europa League),
and the US (MLS) — with Settings, Scores, Fixtures, Standings, and a "My Team" screen (league
position, upcoming/recent fixtures, and who's unavailable for the next match).

This module landed on API-Football after evaluating a few other free/unofficial data sources
(football-data.org, ESPN's unofficial site API, FotMob's unofficial API) that either had tighter
restrictions or weren't worth the ongoing maintenance cost of an undocumented API. Unlike those,
API-Football is a documented, official, stable REST API.

This is a tool the broader LightOS community can install and use for free: the app talks to a
caching proxy (`soccer-proxy`, deployed separately — see "Phase 1 vs. production" below) that holds
a real, paid API-Football key server-side, so a single request budget is shared across every
installed phone instead of each one needing its own key.

## Data source

The app itself talks only to that proxy — see "Phase 1 vs. production" below for the base URL and
why there's no client-side auth. This section documents API-Football itself, which the proxy talks
to server-side. Base URL `https://v3.football.api-sports.io`, authenticated via an
`x-apisports-key` header. Every endpoint and field this tool relies on was verified against real
responses from a real key rather than assumed from documentation — API-Football's docs are accurate
as far as they go, but the free-tier restrictions below (encountered and worked around during
Phase 1, before this build had a paid plan) are not documented anywhere and were only found by
testing.

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
  8 groups of 4 in `standings: [[...], [...], ...]`); MLS is assumed to split into Eastern/Western
  Conference tables the same way, not independently confirmed. The six knockout cups tracked by
  this build (FA Cup, EFL Cup, Coppa Italia, Copa del Rey, DFB-Pokal, Coupe de France) have no
  standings response at all — no code change needed for that, since `Competition.hasStandings`
  keeps them out of the Standings and My Team league pickers in the first place (see "What it
  does" below).
- `GET /injuries` (by `fixture`, or by `league`+`season`+`team`) — My Team's "Unavailable" section.
  Two gotchas: it's a season-long log (one row per fixture a player missed), not a "current state"
  snapshot, so this build calls the fixture-scoped variant against one specific reference fixture
  rather than pulling and filtering the whole season; and despite the name, `reason` includes both
  real injuries and suspension causes ("Red Card", "Yellow Cards") — split into Injured/Suspended
  buckets rather than shown as one undifferentiated "injuries" list.

**League IDs — three verification tiers** (see `SoccerModels.kt`'s doc comment on
`TRACKED_COMPETITIONS` for the same breakdown in code, and `soccer-pro-proxy`'s `app/config.py` for
the whitelist that has to match it):

- **Curl-confirmed against a real response, this project's own history**: 39 (Premier League),
  2 (UEFA Champions League).
- **Corroborated by two independent sources** (general knowledge plus a public GitHub reference
  listing API-Football's commonly-used IDs) but not curl-tested against a live response: 140
  (La Liga), 135 (Serie A), 78 (Bundesliga), 61 (Ligue 1), 3 (UEFA Europa League), 45 (FA Cup),
  143 (Copa del Rey), 137 (Coppa Italia), 81 (DFB-Pokal), 66 (Coupe de France).
- **Recalled from general knowledge only, no independent source found** — the riskiest three,
  worth checking first: 40 (Championship), 48 (EFL Cup), 253 (MLS). A search for a public
  ID-to-name table covering these three specifically came up empty this session (API-Football's
  own such page requires dashboard login).

A wrong ID isn't silently dangerous: the proxy's whitelist has to list the same ID before any
request for it succeeds at all, and the first real request against a wrong one either errors or
comes back as an obviously different competition's real teams — worth a quick look at each new
competition's Scores/Fixtures/Standings once this is deployed, especially the three unconfirmed
ones.

**Also not independently verified — worth a quick real check before relying on them:**

- The `team`+`season`+`from`/`to` fixtures query (My Team's fixture list) — only the
  `league`-scoped equivalent was tested.
- Every `MatchStatus` mapping other than `"FT"` — the free tier's 2022-2024 window made it hard to
  catch a genuinely live or not-yet-started match; the rest of the status-code table comes from
  API-Football's documentation, not a real response.

## Phase 1 vs. production

This build went through a 3-phase plan agreed on before writing any code: (1) build the full
visual/UI/data-flow shape against the free tier's historical 2022-2024 data, (2) design a
lazy/TTL-based caching proxy server so a paid plan's request budget is shared server-side instead
of spent per-installed-phone, (3) point this app at that proxy with a paid plan for real
current-season, live data. **All three phases are done** — this app now talks to
`https://soccer-proxy.ravisolter.com` (`ApiFootballApi.API_BASE` in `SoccerApi.kt`), a FastAPI +
SQLite proxy deployed separately (its own repo, `soccer-pro-proxy`), running via launchd behind a
Cloudflare Tunnel.

The proxy holds the real API-Football key server-side and requires no client-side auth of its own
— confirmed by reading its source: none of its routes check for a client credential, only an
IP-based rate limiter and a league allow-list. This app sends no API key, header, or credential of
any kind; there's no Settings row for one anymore. Season/date logic now uses the real device clock
throughout: `currentSeason()` (`SoccerFormatting.kt`) computes the current API-Football season
number from `todayLocalDate()` (assuming a July season-cutover — see its doc comment for the
caveat), replacing Phase 1's frozen `PHASE1_SEASON`/`phase1Today()` scaffolding, which is gone.

One thing Phase 3 does **not** change: `ApiFootballApi.fetchImageBytes` (team crest images) talks
directly to whatever CDN serves the image, not through the proxy — so crest fetches bypass the
proxy's caching, its request budget, and its rate limiter entirely. That's fine for cost (crest
fetches don't touch API-Football's quota either way) but worth knowing if the proxy's protections
are ever assumed to cover *all* outbound requests from this app.

**There is still no auto-refresh/poll loop in this build.** Refresh is on-demand only: once on
first load, and via a "Refresh now" row in Settings — which also happens to be this build's answer
to the requested "no visible refresh button" feature (see below). The proxy is exactly the kind of
shared, budget-absorbing intermediary that would make a poll loop cheap across every installed
phone (each phone would poll the proxy, not API-Football directly), so this is a real, deliberate
follow-up rather than an oversight — just not part of the Phase 3 migration itself.

## What it does

- **Scores** (default view): today's matches (real device date) across every followed competition,
  grouped by competition. No auto-refresh (see above) — pull-to-date is via Settings' "Refresh now".
  Fans out one request per followed competition, concurrently — with all 15 followed by default
  that's up to 15 concurrent requests per refresh (up from 4 pre-expansion), all cached 5 minutes
  server-side by the proxy; worth keeping in mind against the proxy's shared daily budget even
  though the proxy is exactly what makes this cheap in the first place.
- **Settings**: which of the 15 tracked competitions you follow, your My Team pick (changeable,
  clearable), and the manual refresh action.
- **Fixtures**: pick a followed competition (leagues and cups both), see its matches ±10/+21 days
  around today, grouped by date, auto-scrolled to today.
- **Standings**: pick a followed *league* (knockout cups are excluded here — see `Competition
  .hasStandings` in `SoccerModels.kt` — since they have no table to show), see its table — grouped
  by "Group A"/"Group B"/etc. automatically for UEFA competitions and assumed for MLS's conferences,
  one flat table otherwise (see the Data source section).
- **My Team**: reachable via the star icon in the bottom bar. First use walks through a two-step
  setup (pick a followed *league* — same cup exclusion as Standings, since "league position" needs
  a table — then a team from that league's standings; there's no team-search endpoint verified for
  this build, so the team list comes from data already on screen). Once set, shows league position,
  a handful of upcoming and recent fixtures, and who's unavailable (injured/suspended, split) for
  the team's next match.
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

No API key or setup is needed to run this — the app talks straight to the deployed proxy
(`https://soccer-proxy.ravisolter.com`) and shows real data on first launch. The proxy itself needs
its own API-Football key configured server-side (see the `soccer-pro-proxy` repo), but that's
outside this module.

## Honesty check

This was built by adapting the ESPN/football-data.org variants' already-tested screen/viewmodel
structure to a new, independently-verified API-Football data layer — every DTO shape in
`SoccerModels.kt` traces back to a real `curl` response gathered and reviewed before any Kotlin was
written, not assumed from API-Football's documentation. That said, this is still, like the rest of
this repo, a sandbox build with no Android SDK: neither Phase 1 nor the later Phase 3 proxy
migration was compiled locally before being handed off. A careful manual read-through (imports,
types, sealed-class exhaustiveness, brace/paren balance) stood in for a real build both times.
Treat your first `./gradlew :examples:soccer-football:installDebug` after pulling these changes as
the actual first test — please report back anything that doesn't compile, along with the
"not independently verified" items above (the 13 new-to-this-round league IDs at two different
confidence tiers, the team-scoped fixtures query, every `MatchStatus` code besides `FT`, MLS's
assumed conference-grouped standings, and `currentSeason()`'s July cutover assumption) if you hit
any of them.

The proxy itself (`https://soccer-proxy.ravisolter.com`) was verified live and reachable earlier
this session — `/health`, `/status`, and a real `/standings?league=39&season=2026` call all
returned correct, current data — so the assumption that the proxy is up and working is solid;
what's unverified is only whether this Kotlin change compiles against it, and whether each of the
13 newly-added league IDs is actually correct.

**On the 13 new competitions specifically**: real curl/API verification wasn't possible this round
— the proxy exposes no league-ID-lookup endpoint, and API-Football's own such page
(`dashboard.api-football.com/soccer/ids`) sits behind a login this session has no access to (tried
via the built-in browser; blocked by Cloudflare's bot check before even reaching a login prompt,
and login wasn't attempted regardless — that's the user's account, not something to sign into on
their behalf). What stands in for verification instead: general knowledge, cross-checked against
one public GitHub repo (`zxkane/agentcore-football-api`) that independently lists the same 10 IDs
for La Liga/Bundesliga/Ligue 1/Europa League/the five domestic cups — two independent sources
agreeing is meaningfully better than one, but it's still not the same as a real response. The
Championship, EFL Cup, and MLS IDs have neither corroboration — no second source turned up for
those three despite several searches — so they're the ones most likely to be wrong if any are.
Since the proxy's whitelist gates every request by exact ID, a wrong one fails loudly (an error) or
obviously (a different competition's real teams show up) rather than silently — so the fix, once
you have dashboard access or hit a wrong result, is a one-line change in two places:
`TRACKED_COMPETITIONS` here and `LEAGUE_WHITELIST` in `soccer-pro-proxy`'s `app/config.py`.
