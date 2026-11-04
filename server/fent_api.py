#!/usr/bin/env python3
"""Fent cosmetics + emotes sync API.

Runs on port 6767 (homelab). Endpoints:

  GET  /                     service banner
  GET  /api/health           status + counters
  GET  /api/cosmetics        {"rev":N,"users":{id:{name,at,items}}}
  GET  /api/cosmetics?since=N   only state known after global rev N
  GET  /api/cosmetics/<id>   one player {"id","name","items"}
  PUT  /api/cosmetics/<id>   upsert {"name","items":{...}}; needs Fent-Key
  POST /api/cosmetics/<id>   same as PUT
  DELETE /api/cosmetics/<id> remove a user (needs key)
  GET  /api/emotes           {"erev":N,"emotes":{id:{name,emote,at,infinite}}}
  GET  /api/emotes/<id>      one player's current emote ("" = none)
  PUT  /api/emotes/<id>      upsert {"name","emote","at","infinite"} ("" = stop)
  POST /api/emotes/<id>      same as PUT
  DELETE /api/emotes/<id>    clear an emote (needs key)
  GET  /api/events           SSE stream of realtime changes (data:* lines)

Data lives in fent.sqlite next to this file. Every change is pushed to all
connected SSE clients immediately, so clients sync in real time.
"""

import json
import os
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "fent.sqlite")
CONFIG_PATH = os.path.join(BASE_DIR, "fent_config.json")
PORT = 6767

_LOCK = threading.RLock()
_SSE = threading.Condition(threading.Lock())
_SSE_QUEUES = []
_WRITES = {}
# delete tombstones: clients that were offline during a DELETE learn about it
# from the next ?since= pull instead of keeping the stale row until resync.
_DELETED = {}
_DELETED_EMOTES = {}
_TOMB_CAP = 500


def _connect():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    try:
        conn.execute("PRAGMA journal_mode=WAL")
    except Exception:
        pass
    return conn


def _note_deleted(store, id_, rev):
    with _LOCK:
        store[id_] = rev
        while len(store) > _TOMB_CAP:
            oldest = min(store, key=store.get)
            del store[oldest]

_COSMETIC_TYPES = ["cape", "hat", "wings", "bandana", "shield", "icon", "pet", "aura"]
_ALLOWED_ITEMS = set(range(ord("a"), ord("z") + 1)) | set(range(ord("A"), ord("Z") + 1)) | \
    set(range(ord("0"), ord("9") + 1)) | {ord("_"), ord("-"), ord(".")}


def _sanitize(text: str, limit: int = 64) -> str:
    # mirrors the client's idOk()/sanitize(): [A-Za-z0-9._-] only (ASCII, so a
    # unicode letter can never be stored then fail the client's idOk() check).
    out = "".join(
        ch for ch in str(text)[:limit]
        if ("a" <= ch <= "z" or "A" <= ch <= "Z" or "0" <= ch <= "9" or ch in "_-."))
    return out.strip()


def _load_config():
    try:
        with open(CONFIG_PATH, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except Exception:
        return {}


_CONFIG = _load_config()
API_KEY = str(_CONFIG.get("key", "")).strip()
LISTEN_PORT = int(_CONFIG.get("port", PORT))


def init_db():
    conn = _connect()
    try:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS cosmetics ("
            " id TEXT PRIMARY KEY,"
            " name TEXT NOT NULL DEFAULT '',"
            " at INTEGER NOT NULL DEFAULT 0,"
            " items TEXT NOT NULL DEFAULT '{}',"
            " rev INTEGER NOT NULL DEFAULT 0)")
        conn.execute(
            "CREATE TABLE IF NOT EXISTS emotes ("
            " id TEXT PRIMARY KEY,"
            " name TEXT NOT NULL DEFAULT '',"
            " emote TEXT NOT NULL DEFAULT '',"
            " at INTEGER NOT NULL DEFAULT 0,"
            " infinite INTEGER NOT NULL DEFAULT 0,"
            " erev INTEGER NOT NULL DEFAULT 0)")
        conn.execute("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL DEFAULT '0')")
        conn.commit()
    finally:
        conn.close()


def _read_row(row):
    if row is None:
        return None
    id_, name, at, items, rev = row
    try:
        parsed = json.loads(items)
    except Exception:
        parsed = {}
    return {"id": id_, "name": name, "at": at, "items": parsed, "rev": rev}


def _get_meta(key):
    with _LOCK:
        conn = _connect()
        try:
            row = conn.execute("SELECT v FROM meta WHERE k=?", (key,)).fetchone()
            return int(row[0]) if row else 0
        finally:
            conn.close()


def _bump_meta(key):
    with _LOCK:
        conn = _connect()
        try:
            row = conn.execute("SELECT v FROM meta WHERE k=?", (key,)).fetchone()
            new_rev = (int(row[0]) if row else 0) + 1
            if row:
                conn.execute("UPDATE meta SET v=? WHERE k=?", (str(new_rev), key))
            else:
                conn.execute("INSERT INTO meta (k,v) VALUES (?,?)", (key, str(new_rev)))
            conn.commit()
            return new_rev
        finally:
            conn.close()


def get_global_rev():
    return _get_meta("rev")


def bump_global_rev():
    return _bump_meta("rev")


def get_emote_rev():
    return _get_meta("erev")


def bump_emote_rev():
    return _bump_meta("erev")


def upsert_user(user_id, name, items):
    user_id = _sanitize(user_id)
    name = _sanitize(name or "", 48)
    if not user_id:
        raise ValueError("bad user id")
    clean = {}
    now = int(time.time())
    for key, value in dict(items or {}).items():
        if not isinstance(value, str):
            continue
        token = _sanitize(str(key), 24).lower()
        vid = _sanitize(value, 64)
        if token in _COSMETIC_TYPES and vid and all(ord(c) in _ALLOWED_ITEMS for c in vid):
            clean[token] = vid
    with _LOCK:
        rev = bump_global_rev()
        conn = _connect()
        try:
            conn.execute(
                "INSERT INTO cosmetics (id,name,at,items,rev) VALUES (?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET name=excluded.name, at=excluded.at,"
                " items=excluded.items, rev=excluded.rev",
                (user_id, name, now, json.dumps(clean), rev))
            conn.commit()
        finally:
            conn.close()
    return rev, {"id": user_id, "name": name, "at": now, "items": clean, "rev": rev}


def get_user(user_id):
    user_id = _sanitize(user_id)
    conn = _connect()
    try:
        row = conn.execute(
            "SELECT id,name,at,items,rev FROM cosmetics WHERE id=?", (user_id,)).fetchone()
        if row is None and user_id:
            # clients re-derive ids from display names, so casing can differ
            # ("Zouhmi" vs "zouhmi"); never 404 on a case-only mismatch.
            row = conn.execute(
                "SELECT id,name,at,items,rev FROM cosmetics WHERE lower(id)=lower(?)",
                (user_id,)).fetchone()
    finally:
        conn.close()
    if row is None:
        return None
    data = _read_row(row)
    return {"id": data["id"], "name": data["name"], "at": data["at"], "items": data["items"]}


def delete_user(user_id):
    user_id = _sanitize(user_id)
    with _LOCK:
        rev = bump_global_rev()
        conn = _connect()
        try:
            conn.execute("DELETE FROM cosmetics WHERE id=?", (user_id,))
            conn.commit()
        finally:
            conn.close()
        _note_deleted(_DELETED, user_id, rev)
    return rev


def list_users(since=0):
    conn = _connect()
    try:
        rows = conn.execute("SELECT id,name,at,items,rev FROM cosmetics WHERE rev>? ORDER BY rev",
                            (int(since),)).fetchall()
        total_rev = get_global_rev()
        users = {}
        for row in rows:
            data = _read_row(row)
            users[data["id"]] = {"name": data["name"], "at": data["at"], "items": data["items"]}
        with _LOCK:
            deleted = {k: v for k, v in _DELETED.items() if v > int(since)}
        return {"rev": total_rev, "users": users, "deleted": deleted}
    finally:
        conn.close()


# ---- emotes ---------------------------------------------------------------

def upsert_emote(emote_id, name, emote, at, infinite):
    emote_id = _sanitize(emote_id)
    name = _sanitize(name or "", 48)
    if not isinstance(emote, str):
        emote = ""
    emote = _sanitize(emote, 64)
    at = int(at) if at and int(at) > 0 else int(time.time() * 1000)
    infinite = 1 if str(infinite).lower() in ("1", "true", "yes") else 0
    if not emote_id:
        raise ValueError("bad user id")
    with _LOCK:
        erev = bump_emote_rev()
        conn = _connect()
        try:
            conn.execute(
                "INSERT INTO emotes (id,name,emote,at,infinite,erev) VALUES (?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET name=excluded.name, emote=excluded.emote,"
                " at=excluded.at, infinite=excluded.infinite, erev=excluded.erev",
                (emote_id, name, emote, at, infinite, erev))
            conn.commit()
        finally:
            conn.close()
    return erev, {"id": emote_id, "name": name, "emote": emote, "at": at,
                  "infinite": bool(infinite), "erev": erev}


def get_emote(emote_id):
    emote_id = _sanitize(emote_id)
    conn = _connect()
    try:
        row = conn.execute(
            "SELECT id,name,emote,at,infinite FROM emotes WHERE id=?", (emote_id,)).fetchone()
        if row is None and emote_id:
            row = conn.execute(
                "SELECT id,name,emote,at,infinite FROM emotes WHERE lower(id)=lower(?)",
                (emote_id,)).fetchone()
    finally:
        conn.close()
    if row is None:
        return None
    return {"id": row[0], "name": row[1], "emote": row[2], "at": row[3], "infinite": bool(row[4])}


def delete_emote(emote_id):
    emote_id = _sanitize(emote_id)
    had = get_emote(emote_id) is not None
    with _LOCK:
        erev = bump_emote_rev() if had else get_emote_rev()
        if had:
            conn = _connect()
            try:
                conn.execute("DELETE FROM emotes WHERE id=?", (emote_id,))
                conn.commit()
            finally:
                conn.close()
            _note_deleted(_DELETED_EMOTES, emote_id, erev)
    return erev


def list_emotes(since=0):
    conn = _connect()
    try:
        rows = conn.execute(
            "SELECT id,name,emote,at,infinite FROM emotes WHERE erev>? ORDER BY erev",
            (int(since),)).fetchall()
        emotes = {}
        for row in rows:
            emotes[row[0]] = {"name": row[1], "emote": row[2], "at": row[3], "infinite": bool(row[4])}
        with _LOCK:
            deleted = {k: v for k, v in _DELETED_EMOTES.items() if v > int(since)}
        return {"erev": get_emote_rev(), "emotes": emotes, "deleted": deleted}
    finally:
        conn.close()


# ---- SSE ------------------------------------------------------------------

def sse_register():
    # payloads is a LIST: a single slot lost every event emitted while another
    # one was still queued for that client (broadcasts overwrite each other).
    queue = {"closed": False, "payloads": []}
    with _SSE:
        _SSE_QUEUES.append(queue)
    return queue


def sse_unregister(queue):
    queue["closed"] = True
    with _SSE:
        try:
            _SSE_QUEUES.remove(queue)
        except ValueError:
            pass


def sse_broadcast(payload):
    with _SSE:
        for queue in list(_SSE_QUEUES):
            if not queue.get("closed"):
                queue.setdefault("payloads", []).append(payload)
        _SSE.notify_all()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def handle_one_request(self):
        # the handler instance is REUSED for every request on a keepalive
        # connection (cloudflared pools these), so per-request memo state
        # MUST be reset here or later requests never read their bodies
        self._body_done = False
        self._body_json = {}
        try:
            BaseHTTPRequestHandler.handle_one_request(self)
        except Exception as exc:
            print("handler error: %r" % (exc,), flush=True)
            try:
                self.close_connection = True
            except Exception:
                pass

    def log_message(self, fmt, *args):
        try:
            print("[%s] %s" % (time.strftime("%H:%M:%S"), fmt % args), flush=True)
        except Exception:
            pass

    def _drain(self):
        """Consume any unread request body so the connection stays in sync."""
        self._read_body()

    def _read_body(self):
        """Reads the request body exactly once per request (memoized). Never
        blocks past the declared Content-Length and never double-reads."""
        if getattr(self, "_body_done", False):
            return getattr(self, "_body_json", {})
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except Exception:
            length = 0
        if length <= 0:
            self._body_done = True
            self._body_json = {}
            return {}
        if length > 65536:
            remaining = length
            while remaining > 0:
                chunk = self.rfile.read(min(remaining, 8192))
                if not chunk:
                    break
                remaining -= len(chunk)
            self._body_done = True
            self._body_json = {}
            return {}
        try:
            data = self.rfile.read(length)
        except Exception:
            data = b""
        self._body_done = True
        try:
            parsed = json.loads(data.decode("utf-8") or "{}")
        except Exception:
            parsed = {}
        self._body_json = parsed
        return parsed

    def _check_key(self):
        if not API_KEY:
            return True
        header = self.headers.get("Fent-Key", "")
        return header == API_KEY

    def _boot_auth_or_denied(self):
        if self._check_key():
            return False
        self._drain()
        self._send_json(401, {"error": "unauthorized"})
        return True

    def _real_ip(self):
        # behind the cloudflared tunnel remote_address is always the tunnel
        # itself; the original client arrives in X-Forwarded-For instead.
        try:
            forwarded = self.headers.get("X-Forwarded-For", "")
            if forwarded:
                first = forwarded.split(",")[0].strip()
                if first:
                    return first
        except Exception:
            pass
        return self.client_address[0] or "?"

    def _gate_write(self):
        """Writes require the Fent-Key whenever the server has one configured
        (rows are per-player identity; unkeyed writes let anyone overwrite
        anyone else's cosmetics/emotes). Without a configured key the server
        runs in open dev mode and unkeyed writes are rate limited per real
        client IP. DELETE still always requires the key."""
        if API_KEY:
            if not self._check_key():
                self._drain()
                self._send_json(401, {"error": "unauthorized: Fent-Key required"})
                return False
            return True
        ip = self._real_ip()
        now = time.time()
        with _LOCK:
            start, count = _WRITES.get(ip, (now, 0))
            if now - start > 60.0:
                start, count = now, 0
            if count >= 30:
                self._drain()
                self._send_json(429, {"error": "too many writes; send Fent-Key for full access"})
                return False
            _WRITES[ip] = (start, count + 1)
        return True

    def _send_json(self, code, payload):
        # consume any unread request body first; with HTTP/1.1 keepalive through
        # the tunnel, leftovers would be parsed as the next request line
        self._drain()
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type,Fent-Key,Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path == "/":
            self._send_json(200, {"service": "fent-api", "ok": True, "time": int(time.time())})
            return
        if path == "/api/health":
            with _LOCK:
                conn = _connect()
                try:
                    users = conn.execute("SELECT COUNT(*) FROM cosmetics").fetchone()[0]
                    emotes = conn.execute("SELECT COUNT(*) FROM emotes WHERE emote!=''").fetchone()[0]
                finally:
                    conn.close()
            self._send_json(200, {"service": "fent-api", "ok": True, "users": users,
                                  "emotes": emotes, "rev": get_global_rev(), "erev": get_emote_rev(),
                                  "sse_clients": len(_SSE_QUEUES), "time": int(time.time())})
            return
        if path == "/api/cosmetics":
            since = query.get("since", ["0"])[0]
            try:
                since = max(0, int(since))
            except Exception:
                since = 0
            self._send_json(200, list_users(since))
            return
        if path.startswith("/api/cosmetics/"):
            user_id = path[len("/api/cosmetics/"):]
            if not _sanitize(user_id):
                self._send_json(400, {"error": "bad user id"})
                return
            payload = get_user(user_id)
            if payload is None:
                self._send_json(404, {"error": "not found"})
            else:
                self._send_json(200, payload)
            return
        if path == "/api/emotes":
            since = query.get("since", ["0"])[0]
            try:
                since = max(0, int(since))
            except Exception:
                since = 0
            self._send_json(200, list_emotes(since))
            return
        if path.startswith("/api/emotes/"):
            emote_id = path[len("/api/emotes/"):]
            if not _sanitize(emote_id):
                self._send_json(400, {"error": "bad user id"})
                return
            payload = get_emote(emote_id)
            if payload is None:
                self._send_json(404, {"error": "not found"})
            else:
                self._send_json(200, payload)
            return
        if path == "/api/events":
            self._serve_sse(query)
            return
        self._send_json(404, {"error": "not found"})

    def _serve_sse(self, query):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        queue = sse_register()
        try:
            since = query.get("since", ["0"])[0]
            try:
                since = max(0, int(since))
            except Exception:
                since = 0
            hello = list_users(since)
            hello_emotes = list_emotes()
            self.wfile.write(("data:rev:%d\n" % hello["rev"]).encode("utf-8"))
            self.wfile.write(("data:erev:%d\n" % hello_emotes["erev"]).encode("utf-8"))
            self.wfile.write(b"\n")
            self.wfile.flush()
            while not queue.get("closed"):
                batch = []
                with _SSE:
                    if not queue.get("payloads"):
                        _SSE.wait(timeout=20.0)
                    if queue.get("payloads"):
                        batch = list(queue["payloads"])
                        queue["payloads"] = []
                if not batch:
                    try:
                        self.wfile.write(b":hb\n\n")
                        self.wfile.flush()
                    except Exception:
                        return
                    continue
                # SSE frames MUST carry the "data:" prefix - clients ignore any
                # line without it (FentShare.applyEvent), which silently killed
                # every realtime push before this fix.
                try:
                    for payload in batch:
                        frame = "data:" + json.dumps(payload, separators=(",", ":")) + "\n\n"
                        self.wfile.write(frame.encode("utf-8"))
                    self.wfile.flush()
                except Exception:
                    return
        finally:
            sse_unregister(queue)

    def _handle_write(self, root):
        if root == "cosmetics":
            parsed = urlparse(self.path)
            if not parsed.path.startswith("/api/cosmetics/"):
                self._send_json(404, {"error": "not found"})
                return
            body = self._read_body()
            user_id = parsed.path[len("/api/cosmetics/"):]
            try:
                rev, payload = upsert_user(user_id, body.get("name") or user_id, body.get("items") or {})
            except Exception as exc:
                self._send_json(400, {"error": str(exc)})
                return
            sse_broadcast({"type": "update", "id": payload["id"], "name": payload["name"],
                           "at": payload["at"], "items": payload["items"], "rev": rev})
            self._send_json(200, {"ok": True, "rev": rev, "id": payload["id"]})
            return
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/api/emotes/"):
            self._send_json(404, {"error": "not found"})
            return
        body = self._read_body()
        emote_id = parsed.path[len("/api/emotes/"):]
        try:
            erev, payload = upsert_emote(emote_id, body.get("name") or emote_id,
                                         body.get("emote"), body.get("at"), body.get("infinite"))
        except Exception as exc:
            self._send_json(400, {"error": str(exc)})
            return
        sse_broadcast({"type": "emote", "id": payload["id"], "name": payload["name"],
                       "emote": payload["emote"], "at": payload["at"],
                       "infinite": payload["infinite"], "erev": erev})
        self._send_json(200, {"ok": True, "erev": erev, "id": payload["id"]})

    def do_PUT(self):
        if not self._gate_write():
            return
        parsed = urlparse(self.path)
        root = "cosmetics" if parsed.path.startswith("/api/cosmetics/") else (
            "emotes" if parsed.path.startswith("/api/emotes/") else "")
        if not root:
            self._send_json(404, {"error": "not found"})
            return
        self._handle_write(root)

    do_POST = do_PUT

    def do_DELETE(self):
        if self._boot_auth_or_denied():
            return
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/emotes/"):
            emote_id = parsed.path[len("/api/emotes/"):]
            erev = delete_emote(emote_id)
            sse_broadcast({"type": "emote-stop", "id": emote_id, "emote": "", "erev": erev})
            self._send_json(200, {"ok": True, "erev": erev})
            return
        if not parsed.path.startswith("/api/cosmetics/"):
            self._send_json(404, {"error": "not found"})
            return
        user_id = parsed.path[len("/api/cosmetics/"):]
        rev = delete_user(user_id)
        sse_broadcast({"type": "delete", "id": user_id, "items": {}, "rev": rev})
        self._send_json(200, {"ok": True, "rev": rev})


def main():
    init_db()
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    server.daemon_threads = True
    print("fent-api listening on port %d (key %s)" % (LISTEN_PORT, "set" if API_KEY else "open"), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
