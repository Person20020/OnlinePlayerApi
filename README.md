# OnlinePlayerApi

OnlinePlayerApi is a Spigot plugin that allows access to various data about the currently online players in a server.

## Endpoints

`/api/players` Returns the online player count, and the name, groups, and prefix of each player. If the Essentials X plugin is also found, the afk status of a player will also be returned.

Example response:
```json
{
  "players": [
    {
      "name": "Steve",
      "groups": [
        "default",
        "member",
        "admin"
      ],
      "prefix": "[Admin]",
      "primary_group": "admin",
      "suffix": null,
      "is_afk": false
    },
    {
      "name": "Alex",
      "groups": [
        "default",
        "member",
        "mod"
      ],
      "prefix": "[Mod]",
      "primary_group": "mod",
      "suffix": null,
      "is_afk": true
    }
  ],
  "player_count": 2
}
```

All endpoints require auth. An API key can be generated using `/onlineplayerapi newuser <username>`. It will then return the new key which must be sent with all requests.

Example:
```bash
curl https://127.0.0.1:8080/api/players
  -H "X-API-KEY: <api key>"
  -H "accept: application/json"
```
