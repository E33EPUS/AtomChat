#!/usr/bin/env python3
"""校验 versions/ 里的目标矩阵 —— 本地与 CI 跑的是同一份。

目标写进 versions/targets.json，本脚本是那句声明的执行者：
  * 矩阵的规则（每个 Minecraft 版本都要有 Fabric / NeoForge / Forge 三条）
  * mappings 全目标一致（shared/ 里那些 net.minecraft.* 名字的前提）
  * buildable: true 的目标不许留 unverified
  * 工程目录存在就必须在矩阵里，反之亦然（不许有"没有任何地方编"的源码）
  * 各平台不许自己定义身份键（mod_version 等只在仓库根那一份）
  * 矩阵里写的 java / gradle 与工程里实际的 toolchain / wrapper 一致（防止两边各写一份然后漂移）

用法：
  python3 tools/verify_targets.py                       # 只校验，打印摘要
  python3 tools/verify_targets.py --matrix-out m.json   # 顺便把 CI 矩阵写成 JSON
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOADERS = ["Fabric", "NeoForge", "Forge"]
REQUIRED_FIELDS = ["minecraft", "loader", "mappings", "java", "gradle", "project", "buildable", "layers"]
IDENTITY_KEYS = [
    "mod_id", "mod_name", "mod_license", "mod_group_id",
    "mod_authors", "mod_description", "mod_version",
]

errors: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def load_json(rel: str) -> dict:
    path = ROOT / rel
    if not path.is_file():
        fail(f"{rel} 不存在")
        return {}
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as exc:
        fail(f"{rel} 不是合法 JSON：{exc}")
        return {}


def entries_of(raw: dict) -> dict:
    return {k: v for k, v in raw.items() if not k.startswith("_")}


def read_props(path: pathlib.Path) -> dict:
    out = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def check_root_identity() -> None:
    props = read_props(ROOT / "gradle.properties")
    if not props:
        fail("仓库根的 gradle.properties 不存在或读不出键 —— 身份没有来源")
        return
    missing = [k for k in IDENTITY_KEYS if k not in props]
    if missing:
        fail(f"仓库根的 gradle.properties 缺身份键：{', '.join(missing)}")


def check_targets(targets: dict, layers: dict) -> None:
    if not targets:
        fail("versions/targets.json 里一个目标都没有 —— 文件坏了或被改空了")
        return

    mappings = {}
    seen_combo = set()

    for name, t in targets.items():
        for field in REQUIRED_FIELDS:
            if field not in t:
                fail(f"目标 {name} 缺字段 {field}")
        if any(f not in t for f in REQUIRED_FIELDS):
            continue

        mc, loader = t["minecraft"], t["loader"]
        seen_combo.add((mc, loader))
        mappings.setdefault(t["mappings"], []).append(name)

        for layer in t["layers"]:
            if layer not in layers:
                fail(f"目标 {name} 引用了 layers.json 里没有的层：{layer}")

        project = ROOT / t["project"]
        exists = project.is_dir()

        if t["buildable"] and not exists:
            fail(f"目标 {name} 声明 buildable: true，但工程目录 {t['project']} 不存在")
        if t["buildable"] and t.get("unverified"):
            fail(f"目标 {name} 是 buildable: true，却还留着 unverified：{t['unverified']}")
        if exists:
            for needed in ("build.gradle", "settings.gradle", "gradlew"):
                if not (project / needed).is_file():
                    fail(f"目标 {name} 的工程目录缺 {needed}")
            check_platform_identity(name, project)
            check_platform_toolchain(name, project, t)

    # mappings 必须全一致
    if len(mappings) > 1:
        detail = "；".join(f"{m}: {', '.join(sorted(names))}" for m, names in sorted(mappings.items()))
        fail(f"mappings 在所有目标上必须一致，现在是 {len(mappings)} 种 —— {detail}")

    # 矩阵是规则：每个出现过的 Minecraft 版本都要凑齐三个加载器
    mcs = sorted({mc for mc, _ in seen_combo})
    for mc in mcs:
        for loader in LOADERS:
            if (mc, loader) not in seen_combo:
                fail(f"Minecraft {mc} 缺 {loader} 条目 —— 新增一个版本 = 加三条；实在不做也要写 "
                     f"buildable: false 并在 note 里写清为什么，缺了要在这里看得见")

    # 工程目录存在就必须在矩阵里
    platforms = ROOT / "platforms"
    if platforms.is_dir():
        declared = {t["project"] for t in targets.values() if isinstance(t.get("project"), str)}
        for child in sorted(p for p in platforms.iterdir() if p.is_dir()):
            rel = f"platforms/{child.name}"
            if rel not in declared:
                fail(f"{rel} 存在但不在 targets.json 里 —— 那样它是一份没有任何地方编的源码")


def check_platform_identity(name: str, project: pathlib.Path) -> None:
    props = read_props(project / "gradle.properties")
    dupes = [k for k in IDENTITY_KEYS if k in props]
    if dupes:
        fail(f"目标 {name} 的 gradle.properties 自己定义了身份键：{', '.join(dupes)} —— "
             f"身份只在仓库根那一份里，这里留一份就会各自漂移")


def check_platform_toolchain(name: str, project: pathlib.Path, t: dict) -> None:
    text = (project / "build.gradle").read_text(encoding="utf-8", errors="replace")
    m = re.search(r"JavaLanguageVersion\.of\((\d+)\)", text)
    if m and int(m.group(1)) != int(t["java"]):
        fail(f"目标 {name} 的 targets.json 写 java={t['java']}，"
             f"但 build.gradle 的 toolchain 是 {m.group(1)}")

    wrapper = project / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if wrapper.is_file():
        m = re.search(r"gradle-([0-9][^-]*)", wrapper.read_text(encoding="utf-8", errors="replace"))
        if m and m.group(1) != str(t["gradle"]):
            fail(f"目标 {name} 的 targets.json 写 gradle={t['gradle']}，"
                 f"但 wrapper 是 {m.group(1)}")


def check_layers(layers: dict) -> None:
    if not layers:
        fail("versions/layers.json 里一层都没有")
        return
    for name, d in layers.items():
        has_since = "since" in d
        has_loader = "loader" in d
        if not has_since and not has_loader:
            fail(f"层 {name} 既不写 since 也不写 loader —— 两者都不写就是 shared/，不是层")
        if not isinstance(d.get("populated"), bool):
            fail(f"层 {name} 没有声明 populated（true/false）—— 「空」要是显式声明的状态")
        if has_since and has_loader:
            sub = "both"
        elif has_since:
            sub = "version"
        elif has_loader:
            sub = "loader"
        else:
            continue
        directory = ROOT / "layers" / sub / name
        if d.get("populated") and not directory.is_dir():
            fail(f"层 {name} 声明 populated: true，但目录 layers/{sub}/{name} 不存在")
        if directory.is_dir() and not d.get("populated"):
            files = [p for p in directory.rglob("*") if p.is_file()]
            if files:
                fail(f"层 {name} 声明 populated: false，但目录里有 {len(files)} 个文件 —— "
                     f"要么翻成 true，要么把内容挪走")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix-out", help="把 CI 矩阵写成 JSON 到这个路径")
    args = parser.parse_args()

    targets_raw = load_json("versions/targets.json")
    layers_raw = load_json("versions/layers.json")
    load_json("versions/third-party-apis.json")
    targets = entries_of(targets_raw)
    layers = entries_of(layers_raw)

    check_root_identity()
    check_layers(layers)
    check_targets(targets, layers)

    # 摘要（CI 日志里看这一份）
    print(f"目标 {len(targets)} 条：")
    for name, t in targets.items():
        if not isinstance(t, dict) or "project" not in t:
            continue
        mark = "发" if t.get("buildable") else "不发"
        exists = "有工程" if (ROOT / t["project"]).is_dir() else "无工程"
        print(f"  {name:18} mc={t.get('minecraft'):7} loader={t.get('loader'):9} "
              f"java={t.get('java')} gradle={t.get('gradle')} {mark}/{exists} layers={t.get('layers')}")

    if errors:
        print("\n校验未通过：", file=sys.stderr)
        for e in errors:
            print(f"  ✘ {e}", file=sys.stderr)
        return 1

    print("\n校验通过。")
    for n in notes:
        print(f"  · {n}")

    if args.matrix_out:
        # 产物名后缀在这里算好，不交给工作流里的表达式：
        # GitHub 表达式的 `a && '' || b` 因为空串是 falsy 会静默取到 b，
        # 于是"没验证"的标记会贴到正式产物上（踩过一次）。
        matrix = [
            {
                "target": name,
                "project": t["project"],
                "java": t["java"],
                "buildable": bool(t["buildable"]),
                "suffix": "" if t["buildable"] else "-未验证",
            }
            for name, t in targets.items()
            if isinstance(t, dict) and (ROOT / t.get("project", "")).is_dir()
        ]
        matrix.sort(key=lambda e: e["target"])
        with open(args.matrix_out, "w", encoding="utf-8") as f:
            json.dump(matrix, f)
        print(f"\nCI 矩阵（{len(matrix)} 个目标）已写入 {args.matrix_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
