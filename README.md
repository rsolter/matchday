# Matchday

*A free, open-source Light Phone III tool for live soccer scores, fixtures, and
standings, sourced from [API-Football](https://www.api-football.com).*

## What it is

Live Scores and Fixtures across 22 tracked competitions (the Premier League, Serie A, La Liga,
Bundesliga, Ligue 1, and Europe's Champions League and Europa League among them), league
Standings, a "My Team" view for whichever club you follow, and a per-match Stats / Timeline /
Lineups detail screen.

All of the actual app — the API integration, data models, and UI — lives under
[`examples/matchday`](examples/matchday). See
[that module's README](examples/matchday/README.md) for the full write-up.

## Screenshots

<table>
  <tr>
    <td><img src="Sample%20Photos/Matchday_Scores.png" width="220" alt="Scores screen listing today's matches, grouped by competition"></td>
    <td><img src="Sample%20Photos/Match_View.png" width="220" alt="Match detail screen's Stats tab for a finished match"></td>
  </tr>
  <tr>
    <td><img src="Sample%20Photos/My_Team.png" width="220" alt="My Team screen showing a followed club's league position and recent results"></td>
    <td><img src="Sample%20Photos/Settings.png" width="220" alt="Settings screen listing followed competitions and the My Team pick"></td>
  </tr>
</table>

## Built on Light's SDK

Everything else in this repository — `sdk/`, `plugin/`, `builder/`, `lint-rules/`, `docs/` — is
[Light Phone's own open-source SDK](https://github.com/lightphone/light-sdk) for building LightOS
tools, included as-is (MIT-licensed, see [`LICENSE`](LICENSE)) because Matchday depends on it to
build, run, and be tested in Light's own LightOS emulator (`sdk/emulator` — setup instructions in
[`docs/system_app`](docs/system_app)). This is a fork of that SDK, trimmed to just this one tool.
