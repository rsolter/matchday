# Matchday

*Built by Ravi — a free, open-source Light Phone III tool for live soccer scores, fixtures, and
standings, sourced from [API-Football](https://www.api-football.com).*

## What it is

Scores and Fixtures across 14 tracked competitions (the Premier League, Serie A, La Liga,
Bundesliga, Ligue 1, and Europe's Champions League and Europa League among them), league
Standings, a "My Team" view for whichever club you follow, and a per-match Stats / Timeline /
Lineups detail screen.

All of the actual app — the API integration, data models, and UI — lives under
[`examples/soccer-football`](examples/soccer-football). See
[that module's README](examples/soccer-football/README.md) for the full write-up: the data
source, the free-tier API gotchas verified against real responses, and the build/signing
instructions for producing your own release APK.

## Built on Light's SDK

Everything else in this repository — `sdk/`, `plugin/`, `builder/`, `lint-rules/`, `docs/` — is
[Light Phone's own open-source SDK](https://github.com/lightphone/light-sdk) for building LightOS
tools, included as-is (MIT-licensed, see [`LICENSE`](LICENSE)) because Matchday depends on it to
build, run, and be tested in Light's own LightOS emulator (`sdk/emulator` — setup instructions in
[`docs/system_app`](docs/system_app)).

This is a fork of that SDK, trimmed to just this one tool: the placeholder `tool/` scaffold and
the SDK's other demo apps (`ui-demo`, `weather`, `authenticator`, `audio-demo`) were removed, since
this repo exists to ship Matchday rather than serve as a general SDK starting point. Anyone
wanting to build their own LightOS tool from scratch should start from
[Light's own repo](https://github.com/lightphone/light-sdk) instead, where its full onboarding
docs and current tool-distribution plans live.
