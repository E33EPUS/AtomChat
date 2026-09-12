#!/usr/bin/env python3
"""store_sync.py 的桩测试：用一个本地假 Modrinth 把每条分支都跑出来。

为什么值得有：这个脚本会往一个**外部系统**写东西，而它唯一不能在本机验证的部分恰好是
「令牌真的能用」。所以这里把能验的部分全部验掉 —— 什么时候发请求、发几个、发什么内容、
什么时候**不**发、出错时退出码是多少 —— 剩下那一格交给第一次真跑。

用法：python3 tools/test_store_sync.py
"""
from __future__ import annotations

import http.server
import json
import os
import pathlib
import subprocess
import sys
import threading

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "tools" / "store_sync.py"
BODY = (ROOT / "docs" / "modrinth-description.md").read_text(encoding="utf-8")

failures = []


class Stub(http.server.BaseHTTPRequestHandler):
    """假 Modrinth：记录收到的请求，按测试设定的方式回答。"""

    seen: list = []
    remote_body = ""
    get_status = 200

    def log_message(self, *args):  # 别把桩服务的日志混进测试输出
        pass

    def do_GET(self):
        Stub.seen.append(("GET", self.path, None))
        if Stub.get_status != 200:
            self.send_response(Stub.get_status)
            self.end_headers()
            self.wfile.write(b'{"error": "boom"}')
            return
        body = json.dumps({"body": Stub.remote_body}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_PATCH(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8") if length else ""
        Stub.seen.append(("PATCH", self.path, raw))
        self.send_response(204)
        self.end_headers()


def start_stub() -> tuple[http.server.HTTPServer, str]:
    server = http.server.HTTPServer(("127.0.0.1", 0), Stub)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, f"http://127.0.0.1:{server.server_port}/v2"


def run(base_url: str, *args: str, token: str = "mrp_test", project: str = "abc123") -> tuple[int, str]:
    Stub.seen = []
    env = dict(os.environ)
    env["MODRINTH_BASE_URL"] = base_url
    env["MODRINTH_PROJECT_ID"] = project
    env.pop("MODRINTH_TOKEN", None)
    if token:
        env["MODRINTH_TOKEN"] = token
    done = subprocess.run([sys.executable, str(SCRIPT), *args], capture_output=True, text=True,
                          encoding="utf-8", errors="replace", env=env)
    return done.returncode, (done.stdout or "") + (done.stderr or "")


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f" —— {detail}" if detail and not ok else ""))
    if not ok:
        failures.append(name)


def main() -> int:
    server, base = start_stub()
    try:
        print("store_sync.py 桩测试：")

        Stub.remote_body = ""
        rc, out = run(base, token="")
        check("没有令牌时不发任何请求", rc == 0 and "没有 MODRINTH_TOKEN" in out and Stub.seen == [],
              f"rc={rc} seen={Stub.seen}")

        rc, out = run(base)  # 默认演练
        patched = [s for s in Stub.seen if s[0] == "PATCH"]
        check("演练模式只读不写", rc == 0 and "演练模式" in out and not patched and
              any(s[0] == "GET" for s in Stub.seen), f"rc={rc} seen={Stub.seen}")

        rc, out = run(base, "--apply")
        patched = [s for s in Stub.seen if s[0] == "PATCH"]
        payload = json.loads(patched[0][2]) if patched else {}
        check("--apply 恰好发一个 PATCH，且只带 body", rc == 0 and len(patched) == 1
              and list(payload) == ["body"] and payload["body"] == BODY,
              f"rc={rc} seen={Stub.seen[:2]}")
        check("PATCH 打在项目 id 上", patched and patched[0][1] == "/v2/project/abc123",
              f"{patched[:1]}")

        Stub.remote_body = BODY
        rc, out = run(base, "--apply")
        check("远端已一致时不写", rc == 0 and "一致" in out
              and not [s for s in Stub.seen if s[0] == "PATCH"], f"rc={rc} seen={Stub.seen}")

        Stub.remote_body = ""
        Stub.get_status = 500
        rc, out = run(base, "--apply")
        check("读项目失败时非 0 退出", rc == 1 and "HTTP 500" in out, f"rc={rc}")
        Stub.get_status = 200

        rc, out = run(base, "--apply", project="UNSET")
        check("项目 id 未配置时给指引且不动手", rc == 0 and "还没配项目 id" in out and Stub.seen == [],
              f"rc={rc} seen={Stub.seen}")
    finally:
        server.shutdown()

    print()
    if failures:
        print(f"{len(failures)} 条失败：" + "、".join(failures))
        return 1
    print("全部通过。")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
