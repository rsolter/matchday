#!/usr/bin/env python3
"""
Reports how often API-Football's player headshot images are missing, across
each top-5-league squad, for Matchday's new lineup-headshot feature.

WHY THIS IS A STANDALONE SCRIPT, NOT SOMETHING CLAUDE RAN FOR YOU:
This session's sandbox has no network path to api-sports.io/api-football.com
at all (confirmed by testing both the app's own proxy and the raw media CDN
directly -- both come back "CONNECT tunnel failed, response 403" from this
sandbox's own egress policy, unrelated to your API key or the proxy's IP
allow-list). Every other verification in this project has hit the same wall
and been worked around by asking you to run a curl yourself -- this is that,
packaged as a script instead of a one-off command.

WHAT IT DOES
For each of the 5 leagues, pulls every team's full squad via
GET /players/squads?team={id} (one call per team -- this endpoint returns
each squad member's `photo` field, which is API-Football's own
media.api-sports.io/football/players/{id}.png URL -- the exact convention
Matchday's new fetchPlayerPhotos() constructs client-side), then fetches each
player's photo URL and checks whether it looks like a real headshot or
API-Football's "no photo available" placeholder.

REQUIRES
    pip install requests
    export API_FOOTBALL_KEY=<your api-sports.io key>   # same key your proxy holds

IMPORTANT CAVEAT -- READ BEFORE TRUSTING THE NUMBERS
`photo_is_placeholder()` below guesses at a placeholder based on response
size (missing/default photos are usually served as a small fixed-size image
rather than a 404). That threshold is a starting guess, NOT verified against
a real response in this session (no network access to check it against). Run
this once, then open a couple of the URLs it flagged as "missing" in a
browser to confirm they're actually blank/placeholder and not just small
real photos -- adjust PLACEHOLDER_MAX_BYTES if the threshold is off.

REQUEST BUDGET
~5 leagues x (1 teams call + ~20-25 squad calls) = ~130 JSON requests, plus
one image GET per player (~2,500-3,000 players across 5 leagues) -- well
under the 7,500/day API-Football cap this project is on, and the 0.25s
sleep between squad calls keeps well clear of the 300/min rate cap too. The
image fetches hit the unauthenticated media CDN, not the rate-limited API,
so they aren't part of that budget at all.
"""

import os
import sys
import time

import requests

API_KEY = os.environ.get("API_FOOTBALL_KEY")
if not API_KEY:
    sys.exit("Set API_FOOTBALL_KEY to your api-sports.io key first.")

API_BASE = "https://v3.football.api-sports.io"
HEADERS = {"x-apisports-key": API_KEY}

# Same league ids TRACKED_COMPETITIONS uses in SoccerModels.kt -- already
# curl-verified against this app's own proxy earlier in this project, not
# re-guessed here.
LEAGUES = {
    39: "Premier League",
    140: "La Liga",
    135: "Serie A",
    78: "Bundesliga",
    61: "Ligue 1",
}

# API-Football's "season" param is the year a season STARTS. Today is
# 2026-09-21, so the 2025-26 season (season=2025) is the current one --
# double-check this is still right by the time you run this.
SEASON = 2025

# See the caveat in the module docstring -- unverified guess, tune after a
# manual spot-check of the first run's output.
PLACEHOLDER_MAX_BYTES = 2000

REQUEST_PAUSE_SECONDS = 0.25


def get(session: requests.Session, path: str, **params) -> list:
    resp = session.get(f"{API_BASE}/{path}", headers=HEADERS, params=params, timeout=20)
    resp.raise_for_status()
    body = resp.json()
    errors = body.get("errors")
    if errors:
        raise RuntimeError(f"{path} {params} -> API error: {errors}")
    return body.get("response", [])


def photo_is_missing(session: requests.Session, url: str | None) -> bool:
    if not url:
        return True
    try:
        resp = session.get(url, timeout=15)
    except requests.RequestException:
        return True  # network hiccup counts as "couldn't confirm a real photo"
    if resp.status_code != 200:
        return True
    return len(resp.content) < PLACEHOLDER_MAX_BYTES


def main() -> None:
    session = requests.Session()
    print(f"Season {SEASON}-{SEASON + 1}. Placeholder threshold: <{PLACEHOLDER_MAX_BYTES} bytes (unverified, see docstring).\n")

    league_rows = []
    for league_id, league_name in LEAGUES.items():
        teams = get(session, "teams", league=league_id, season=SEASON)
        if not teams:
            print(f"!! No teams returned for {league_name} (league={league_id}, season={SEASON}) -- season value likely wrong for this league.")
            continue

        total_players = 0
        missing_players = 0
        per_team = []
        for entry in teams:
            team = entry["team"]
            squads = get(session, "players/squads", team=team["id"])
            if not squads:
                print(f"  (no squad data for {team['name']} -- skipped)")
                continue
            players = squads[0]["players"]
            team_missing = sum(1 for p in players if photo_is_missing(session, p.get("photo")))
            total_players += len(players)
            missing_players += team_missing
            per_team.append((team["name"], team_missing, len(players)))
            time.sleep(REQUEST_PAUSE_SECONDS)

        league_rows.append((league_name, missing_players, total_players, per_team))

    print(f"{'League':<20}{'Missing':>10}{'Total':>10}{'% missing':>12}")
    print("-" * 52)
    for league_name, missing, total, per_team in league_rows:
        pct = (100 * missing / total) if total else 0.0
        print(f"{league_name:<20}{missing:>10}{total:>10}{pct:>11.1f}%")
        for name, m, t in sorted(per_team, key=lambda row: -row[1]):
            if m:
                print(f"    {name:<30}{m}/{t} missing")
    print()


if __name__ == "__main__":
    main()
