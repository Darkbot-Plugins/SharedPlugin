#!/usr/bin/env python3
"""
Remote Panel mock backend for local development.

Endpoints:
- GET  /health
- POST /v1/telemetry
- GET  /v1/commands/next?botPublicId=<id>
- POST /v1/commands/enqueue
- GET  /v1/state
"""

from __future__ import annotations

import argparse
import json
import queue
import threading
from dataclasses import dataclass, field
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class CommandItem:
    request_id: str
    action: str
    parameter: str = ""
    created_at: str = field(default_factory=now_iso)

    def as_dict(self) -> Dict[str, Any]:
        return {
            "requestId": self.request_id,
            "action": self.action,
            "parameter": self.parameter,
            "createdAt": self.created_at,
        }


@dataclass
class State:
    commands_by_bot: Dict[str, "queue.Queue[CommandItem]"] = field(default_factory=dict)
    telemetry_by_bot: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    telemetry_count: int = 0
    lock: threading.Lock = field(default_factory=threading.Lock)
    required_token: str = ""
    legacy_account_id: Optional[str] = None
    legacy_server_response: Dict[str, Any] = field(
        default_factory=lambda: {
            "action": "none",
            "id": "all",
            "parameter": "",
            "tick": int(datetime.now().timestamp() * 1000),
        }
    )

    def enqueue(self, bot_id: str, command: CommandItem) -> None:
        with self.lock:
            if bot_id not in self.commands_by_bot:
                self.commands_by_bot[bot_id] = queue.Queue()
            self.commands_by_bot[bot_id].put(command)

    def pop_next(self, bot_id: str) -> Optional[CommandItem]:
        with self.lock:
            q = self.commands_by_bot.get(bot_id)
            if q is None or q.empty():
                return None
            return q.get_nowait()

    def pending_count(self, bot_id: str) -> int:
        with self.lock:
            q = self.commands_by_bot.get(bot_id)
            return 0 if q is None else q.qsize()

    def add_telemetry(self, payload: Dict[str, Any]) -> None:
        bot_id = str(payload.get("botPublicId", "")).strip() or "unknown"
        with self.lock:
            self.telemetry_by_bot[bot_id] = {
                "payload": payload,
                "receivedAt": now_iso(),
            }
            self.telemetry_count += 1

    def snapshot(self) -> Dict[str, Any]:
        with self.lock:
            pending = {k: v.qsize() for k, v in self.commands_by_bot.items()}
            telemetry = {k: v["receivedAt"] for k, v in self.telemetry_by_bot.items()}
            return {
                "telemetryCount": self.telemetry_count,
                "pendingByBot": pending,
                "lastTelemetryAtByBot": telemetry,
                "legacyAccountId": self.legacy_account_id,
                "legacyServerResponse": self.legacy_server_response,
            }

    def set_legacy_response(self, action: str, account_id: str = "all", parameter: str = "") -> None:
        with self.lock:
            self.legacy_server_response = {
                "action": action,
                "id": account_id or "all",
                "parameter": parameter or "",
                "tick": int(datetime.now().timestamp() * 1000),
            }


class Handler(BaseHTTPRequestHandler):
    state: State = State()
    panel_html = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Remote Panel Panel</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 24px; background: #f5f7fb; color: #111; }
    .card { background: #fff; border-radius: 10px; padding: 16px; margin-bottom: 16px; box-shadow: 0 2px 8px rgba(0,0,0,.08); }
    input, button, select { padding: 8px 10px; margin: 4px; }
    button { cursor: pointer; }
    pre { background: #0f172a; color: #e2e8f0; padding: 12px; border-radius: 8px; overflow: auto; }
  </style>
</head>
<body>
  <h2>Remote Panel - Local Panel</h2>
  <div class="card">
    <label>Bot ID (pilot name):</label>
    <input id="botId" value="bot-local-1" />
    <button onclick="refreshState()">Refresh State</button>
  </div>

  <div class="card">
    <h3>Quick Commands</h3>
    <button onclick="sendCmd('start')">Start</button>
    <button onclick="sendCmd('stop')">Stop</button>
    <button onclick="sendCmd('refresh')">Refresh</button>
    <button onclick="sendCmd('reset_stats')">Reset Stats</button>
    <br />
    <input id="moduleId" placeholder="module id (e.g. npc_killer)" />
    <button onclick="sendCmd('set_module', document.getElementById('moduleId').value)">Set Module</button>
    <br />
    <input id="mapId" placeholder="map id (e.g. 14)" />
    <button onclick="sendCmd('set_map', document.getElementById('mapId').value)">Set Map</button>
  </div>

  <div class="card">
    <h3>State</h3>
    <pre id="stateBox">{}</pre>
  </div>

  <script>
    async function sendCmd(action, parameter = "") {
      const botPublicId = document.getElementById('botId').value.trim();
      if (!botPublicId) return alert('Bot ID gerekli');
      const res = await fetch('/v1/commands/enqueue', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ botPublicId, action, parameter })
      });
      const data = await res.json();
      alert(JSON.stringify(data, null, 2));
      refreshState();
    }

    async function refreshState() {
      const res = await fetch('/v1/state');
      const data = await res.json();
      document.getElementById('stateBox').textContent = JSON.stringify(data, null, 2);
    }

    refreshState();
    setInterval(refreshState, 3000);
  </script>
</body>
</html>
"""

    def _send_json(self, code: int, payload: Dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, code: int, body: str, content_type: str = "text/plain; charset=utf-8") -> None:
        encoded = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _read_json(self) -> Dict[str, Any]:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0:
            return {}
        raw = self.rfile.read(content_length).decode("utf-8", errors="ignore")
        if not raw.strip():
            return {}
        return json.loads(raw)

    def _authorized(self) -> bool:
        required = self.state.required_token.strip()
        if not required:
            return True
        auth = self.headers.get("Authorization", "")
        return auth == f"Bearer {required}"

    def _process_legacy_command(self, command: str) -> Dict[str, Any]:
        cmd = (command or "").strip()
        if not cmd:
            return {"ok": False, "error": "empty_command"}

        if cmd.startswith("switch:"):
            parts = cmd.split(":", 1)
            self.state.legacy_account_id = parts[1].strip() if len(parts) > 1 else None
            return {
                "ok": True,
                "mode": "switch",
                "legacyAccountId": self.state.legacy_account_id,
            }

        if cmd == "multiple":
            self.state.legacy_account_id = None
            return {"ok": True, "mode": "multiple"}

        if cmd.startswith("action:"):
            # Legacy format: action:<action>:<parameter>
            parts = cmd.split(":", 2)
            action = parts[1].strip() if len(parts) > 1 else ""
            parameter = parts[2].strip() if len(parts) > 2 else ""
            account = self.state.legacy_account_id or "all"
            if action:
                self.state.set_legacy_response(action, account, parameter)

            # Also mirror to v2 command queue when account is selected.
            if account != "all" and action:
                self.state.enqueue(account, CommandItem(request_id=f"legacy-{int(datetime.now().timestamp()*1000)}", action=action, parameter=parameter))

            return {"ok": True, "mode": "action", "response": self.state.legacy_server_response}

        return {"ok": False, "error": "unsupported_command"}

    def do_GET(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler naming)
        parsed = urlparse(self.path)

        if parsed.path == "/":
            self._send_text(HTTPStatus.OK, self.panel_html, "text/html; charset=utf-8")
            return

        if parsed.path == "/health":
            self._send_json(HTTPStatus.OK, {"ok": True, "time": now_iso()})
            return

        if not self._authorized():
            self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return

        if parsed.path == "/v1/commands/next":
            params = parse_qs(parsed.query)
            bot_id = (params.get("botPublicId", [""])[0] or "").strip()
            if not bot_id:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "botPublicId is required"})
                return
            cmd = self.state.pop_next(bot_id)
            if cmd is None:
                self._send_json(
                    HTTPStatus.OK,
                    {"requestId": "", "action": "noop", "parameter": ""},
                )
                return
            self._send_json(HTTPStatus.OK, cmd.as_dict())
            return

        if parsed.path == "/v1/state":
            self._send_json(HTTPStatus.OK, self.state.snapshot())
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)

        if not self._authorized():
            self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return

        if parsed.path == "/":
            # Legacy DKS server compatibility endpoint.
            # Accepts telemetry payload and returns current server response JSON.
            try:
                payload = self._read_json()
            except json.JSONDecodeError:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
                return

            # Legacy payload has hero.id and (optionally) hero.username.
            hero = payload.get("hero") if isinstance(payload, dict) else None
            bot_public_id = ""
            if isinstance(hero, dict):
                bot_public_id = str(hero.get("username") or hero.get("id") or "").strip()
            if not bot_public_id:
                bot_public_id = str(payload.get("botPublicId", "")).strip() if isinstance(payload, dict) else ""
            if not bot_public_id:
                bot_public_id = "unknown"

            normalized = dict(payload) if isinstance(payload, dict) else {"raw": payload}
            normalized["botPublicId"] = bot_public_id
            self.state.add_telemetry(normalized)
            self._send_json(HTTPStatus.OK, self.state.legacy_server_response)
            return

        if parsed.path == "/v1/telemetry":
            try:
                payload = self._read_json()
            except json.JSONDecodeError:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
                return
            self.state.add_telemetry(payload)
            self._send_json(HTTPStatus.OK, {"ok": True})
            return

        if parsed.path == "/v1/commands/enqueue":
            try:
                payload = self._read_json()
            except json.JSONDecodeError:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
                return

            bot_id = str(payload.get("botPublicId", "")).strip()
            action = str(payload.get("action", "")).strip()
            parameter = str(payload.get("parameter", "")).strip()
            request_id = str(payload.get("requestId", "")).strip() or f"req-{int(datetime.now().timestamp() * 1000)}"

            if not bot_id or not action:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "botPublicId and action are required"})
                return

            item = CommandItem(request_id=request_id, action=action, parameter=parameter)
            self.state.enqueue(bot_id, item)
            self._send_json(
                HTTPStatus.CREATED,
                {
                    "ok": True,
                    "queued": item.as_dict(),
                    "pendingForBot": self.state.pending_count(bot_id),
                },
            )
            return

        if parsed.path == "/v1/legacy/command":
            try:
                payload = self._read_json()
            except json.JSONDecodeError:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
                return
            command = str(payload.get("command", "")).strip()
            result = self._process_legacy_command(command)
            if result.get("ok"):
                self._send_json(HTTPStatus.OK, result)
            else:
                self._send_json(HTTPStatus.BAD_REQUEST, result)
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
        # Compact server logs for local dev.
        print(f"[mock-backend] {self.address_string()} - {format % args}")


def main() -> None:
    parser = argparse.ArgumentParser(description="RemotePanel mock backend")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18085)
    parser.add_argument("--token", default="", help="Optional bearer token")
    args = parser.parse_args()

    Handler.state.required_token = args.token.strip()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Mock backend started on http://{args.host}:{args.port}")
    if Handler.state.required_token:
        print("Auth mode: ON (Bearer token required)")
    else:
        print("Auth mode: OFF")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()

