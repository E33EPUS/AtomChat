#!/usr/bin/env python3
"""把仓库里的商店文案同步到 Modrinth（CurseForge 没有这个接口，见文件末尾）。

为什么要有这个脚本：Modrinth 的项目正文在网页上只能整段贴，而正文的真源是
`docs/modrinth-description.md` —— 两者一旦分居两地就会漂移。这个脚本把「真源 → 商店」
变成一条命令，并且**默认只演练**（打印会发什么、不发）。

用法：
    python3 tools/store_sync.py                  # 演练（默认）
    python3 tools/store_sync.py --apply          # 真发 PATCH
    python3 tools/store_sync.py --base-url …     # 测试用（指向本地桩服务）

环境变量：
    MODRINTH_TOKEN   Modrinth 个人访问令牌，需要 PROJECT_WRITE 权限

退出码：0 = 完成或「还没配好」（不视为失败，与本仓 CF/Modrinth 的 onlyIf 惯例一致）；
非 0 = 真的出错了（HTTP 失败、令牌无效、项目 id 不存在）。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
BODY_FILE = ROOT / "docs" / "modrinth-description.md"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
DEFAULT_BASE_URL = "https://api.modrinth.com/v2"


def project_id() -> str:
    """项目 id 与版本号同源：都从仓库根 gradle.properties 读，绝不另存一份。

    环境变量 `MODRINTH_PROJECT_ID` 可以覆盖它 —— 只为测试那些分支（未配置 / 换了项目），
    正常使用不放这个变量。
    """
    override = os.environ.get("MODRINTH_PROJECT_ID", "").strip()
    if override:
        return override
    for line in GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines():
        if line.startswith("modrinth_project_id="):
            return line.split("=", 1)[1].strip()
    return ""


def request(method: str, url: str, token: str, payload: dict | None = None) -> tuple[int, dict]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", token)
    req.add_header("User-Agent", "E33EPUS/AtomChat store-sync")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8") or "{}"
            return response.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else {})
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")[:400]
        return e.code, {"error": detail}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true",
                        help="真的发送 PATCH；不加这个参数只演练")
    parser.add_argument("--base-url", default=os.environ.get("MODRINTH_BASE_URL", DEFAULT_BASE_URL),
                        help="API 根地址（测试时可指向本地桩服务）")
    args = parser.parse_args()

    body = BODY_FILE.read_text(encoding="utf-8")
    pid = project_id()
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    print(f"正文来源: {BODY_FILE.relative_to(ROOT).as_posix()}（{len(body)} 字符）")
    print(f"项目 id : {pid or '(gradle.properties 里没有 modrinth_project_id)'}")

    if not pid or pid == "UNSET":
        print("\n还没配项目 id，什么也没做。Modrinth 上架需要三步：")
        print("  1. 建项目（网页上建，或用 POST /v2/project，需要 PROJECT_CREATE 权限的令牌）；")
        print("  2. 把项目 id 填进仓库根 gradle.properties 的 modrinth_project_id；")
        print("  3. 把令牌加进仓库 secret MODRINTH_TOKEN —— 之后 release.yml 的 :modrinth 腿")
        print("     会跟着 v* 标签自动发版，这个脚本负责把正文同步过去。")
        return 0

    if not token:
        print("\n没有 MODRINTH_TOKEN，什么也没发（本地演练只看正文与项目 id）。")
        return 0

    status, project = request("GET", f"{args.base_url}/project/{pid}", token)
    if status != 200:
        print(f"\n读项目失败：HTTP {status} {project.get('error', '')}", file=sys.stderr)
        return 1

    remote = project.get("body") or ""
    if remote == body:
        print("\n远端正文与仓库一致，无需同步。")
        return 0

    print(f"\n远端正文 {len(remote)} 字符，与仓库不同（差 {abs(len(remote) - len(body))} 字符）。")
    if not args.apply:
        print("演练模式：没有发送。加 --apply 才会推。")
        return 0

    status, result = request("PATCH", f"{args.base_url}/project/{pid}", token, {"body": body})
    if status not in (200, 204):
        print(f"同步失败：HTTP {status} {result.get('error', '')}", file=sys.stderr)
        return 1
    print("已同步（只改了 body 一个字段，其余字段不碰）。")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())

# CurseForge 为什么没有对应的腿：官方 Upload API 只有四个端点
# （/api/projects/{id}/upload-file、/update-file、maven、本地化导入导出），
# 没有改项目描述的接口 —— 那里的正文只能人工贴。证据见
# https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api
