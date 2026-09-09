# Matchday

*A free, open-source Light Phone III tool for live soccer scores, fixtures, and
standings, sourced from [API-Football](https://www.api-football.com).*

## What it is

Live Scores and Fixtures across 14 tracked competitions (the Premier League, Serie A, La Liga,
Bundesliga, Ligue 1, and Europe's Champions League and Europa League among them), league
Standings, a "My Team" view for whichever club you follow, and a per-match Stats / Timeline /
Lineups detail screen.

All of the actual app — the API integration, data models, and UI — lives under
[`examples/matchday`](examples/matchday). See
[that module's README](examples/matchday/README.md) for the full write-up.

## Built on Light's SDK

Everything else in this repository — `sdk/`, `plugin/`, `builder/`, `lint-rules/`, `docs/` — is
[Light Phone's own open-source SDK](https://github.com/lightphone/light-sdk) for building LightOS
tools, included as-is (MIT-licensed, see [`LICENSE`](LICENSE)) because Matchday depends on it to
build, run, and be tested in Light's own LightOS emulator (`sdk/emulator` — setup instructions in
[`docs/system_app`](docs/system_app)). This is a fork of that SDK, trimmed to just this one tool.
