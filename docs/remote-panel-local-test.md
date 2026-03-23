# Remote Panel Local Test

## 1) Start mock backend

```powershell
cd "C:\Users\canda\OneDrive\Desktop\Eklenti Ã‡alÄ±ÅŸmalarÄ±m\darkbot\SharedPlugin"
python .\tools\mock_backend.py --host 127.0.0.1 --port 18085
```

Optional token mode:

```powershell
python .\tools\mock_backend.py --host 127.0.0.1 --port 18085 --token my-dev-token
```

## 2) Configure plugin (Darkbot UI)

- Feature: `Remote Panel`
- `enabled`: `true`
- `telemetry_url`: `http://127.0.0.1:18085/v1/telemetry`
- `command_url`: `http://127.0.0.1:18085/v1/commands/next`
- `api_token`: empty (or `my-dev-token` if token mode enabled)
- `telemetry_interval_seconds`: `5`
- `command_poll_interval_millis`: `1000`

`botPublicId` is auto-generated from the pilot username.

## 3) Backend endpoint smoke test

```powershell
cd "C:\Users\canda\OneDrive\Desktop\Eklenti Ã‡alÄ±ÅŸmalarÄ±m\darkbot\SharedPlugin"
.\tools\test-remote-panel.ps1 -BaseUrl "http://127.0.0.1:18085" -BotId "bot-local-1"
```

With token:

```powershell
.\tools\test-remote-panel.ps1 -BaseUrl "http://127.0.0.1:18085" -BotId "bot-local-1" -Token "my-dev-token"
```

