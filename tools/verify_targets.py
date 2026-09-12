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
import hashlib
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


def check_targets(targets: dict, layers: dict, aliases: dict) -> None:
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
                continue
            gap = predicate_gap(t, layer, layers[layer])
            if gap:
                fail(f"目标 {name} 挂了层 {layer}，但它满足不了该层的谓词：{gap}")

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

    # 映射家族不止一个时（本仓就是：Fabric 走 Yarn，另两端走官方名），
    # 每个【有两个以上成员在建】的家族都要有对应的映射层 —— 那种家族里的平行副本是可共享的，
    # 让它们各存一份就是把「三端同步」缩小成「两端同步」。只有一个成员的家族不必成层（没人可共享）；
    # 层存在时，未挂该层的目标要在自己平台目录里保留同路径副本，那份副本由孪生检查盯着。
    if len(mappings) > 1:
        for family in sorted(mappings):
            built = [n for n in mappings[family] if (ROOT / targets[n]["project"]).is_dir()]
            declared = [n for n, d in layers.items()
                        if layer_axes(d) == ["mappings"] and d.get("mappings") == family]
            if len(built) > 1 and not declared:
                fail(f"mappings={family} 有 {len(built)} 个在建目标（{', '.join(built)}），"
                     f"但 layers.json 里没有纯映射层声明 mappings={family} —— "
                     f"它们本可共用一份，现在只能各存一份没人盯着的手抄副本")
                continue
            for layer_name in declared:
                check_mapping_twins(layer_name, layers[layer_name], targets, family,
                                    aliases.get(f"layers/mapping/{layer_name}", {}))

    # 反方向：某一家族已经有映射层了，同家族的目标就必须挂它 —— 否则它会留一份私藏副本，
    # 而那份副本与层里的内容本应逐字相同，没有任何东西会告诉你它慢慢不一样了。
    for name, t in targets.items():
        for layer_name, d in layers.items():
            if layer_axes(d) != ["mappings"] or d.get("mappings") != t.get("mappings"):
                continue
            if layer_name not in t["layers"]:
                fail(f"目标 {name} 的 mappings 是 {t.get('mappings')}，映射层 {layer_name} 就是为这个家族准备的，"
                     f"但它的 layers 里没有这一层 —— 那样它会留一份没人盯着的私藏副本")

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


def layer_axes(d: dict) -> list:
    return [a for a in ("since", "loader", "mappings") if a in d]


def layer_dir(layer_name: str, d: dict) -> pathlib.Path:
    """目录由声明的轴推出来 —— 声明与目录不会互相矛盾。"""
    axis_dirs = {"since": "version", "loader": "loader", "mappings": "mapping"}
    return ROOT / "layers" / "+".join(sorted(axis_dirs[a] for a in layer_axes(d))) / layer_name


def version_at_least(have: str, want: str) -> bool:
    def parts(v):
        return [int(p) if str(p).isdigit() else 0 for p in str(v).split(".")]

    a, b = parts(have), parts(want)
    for i in range(max(len(a), len(b))):
        x, y = (a[i] if i < len(a) else 0), (b[i] if i < len(b) else 0)
        if x != y:
            return x > y
    return True


def predicate_gap(t: dict, layer_name: str, d: dict):
    """目标挂了这个层，但它满足不了该层的谓词 —— 返回原因，满足则 None。"""
    for axis in layer_axes(d):
        want = str(d[axis])
        if axis == "since":
            if not version_at_least(t["minecraft"], want):
                return f"Minecraft {t['minecraft']} 够不到 since {want}"
        else:
            have = str(t.get(axis))
            if have != want:
                return f"它的 {axis} 是 {have}，层要求 {axis}={want}"
    return None


def digest(path: pathlib.Path) -> str:
    """行尾无关的比较。

    工作区是 CRLF、仓库里存的是 LF（`.gitattributes` 的 `* text=auto` 管这件事），
    所以直读字节会把"只有行尾不同"判成内容不同 —— 那样"孪生副本逐字相同"这条检查
    会假阴性（该报的不报）。比较前先折叠行尾，让这条检查只看内容。
    """
    return hashlib.sha1(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def check_mapping_twins(layer_name: str, d: dict, targets: dict, family: str, aliases: dict) -> None:
    """映射层的代价是「另一个家族保留同路径的孪生副本」。这份副本是手抄的，
    所以必须有人在盯着：少一个、或者其实逐字相同（那它本该进 shared/）都要红。

    aliases 是路径别名：有的类在两个家族上包路径不同（Fabric 侧故意住在原版包下，
    借包私有访问达成与 AT 同样的目的）。别名把这一处写下来，检查照样按对应路径去找，
    找不到一样红 —— 别名不是豁免，只是让例外变成可检查的声明。"""
    base = layer_dir(layer_name, d)
    if not base.is_dir():
        return
    files = [p.relative_to(base).as_posix() for p in base.rglob("*.java")]
    for rel in sorted(files):
        if not rel.startswith(("src/main/java/", "src/test/java/")):
            continue
        twin_rel = aliases.get(rel, rel)
        for name, t in targets.items():
            if t.get("mappings") == family:
                continue
            project = ROOT / t["project"]
            if not project.is_dir():
                continue  # 还没建起来的目标不欠副本
            twin = project / twin_rel
            if not twin.is_file():
                hint = f"（按别名找的是 {twin_rel}）" if twin_rel != rel else ""
                fail(f"映射层 {layer_name} 里有 {rel}，但目标 {name}"
                     f"（mappings={t.get('mappings')}）的平台目录里没有对应的孪生副本{hint} —— "
                     f"这条路线的代价就是那份平行副本，少了它意味着这个类在那个目标上根本不存在")
            elif digest(twin) == digest(base / rel):
                fail(f"{rel} 的孪生副本与映射层里那份【逐字相同】—— 它其实是映射中立的，"
                     f"应该进 shared/，不必在这里共存两份")


def check_layers(layers: dict) -> None:
    if not layers:
        fail("versions/layers.json 里一层都没有")
        return
    for name, d in layers.items():
        axes = layer_axes(d)
        if not axes:
            fail(f"层 {name} 一个轴都没钉（since / loader / mappings）—— 一个都不写就是 shared/，不是层")
        if not isinstance(d.get("populated"), bool):
            fail(f"层 {name} 没有声明 populated（true/false）—— 「空」要是显式声明的状态")
        directory = layer_dir(name, d)
        if d.get("populated") and not directory.is_dir():
            fail(f"层 {name} 声明 populated: true，但目录 {directory.relative_to(ROOT).as_posix()} 不存在")
        if directory.is_dir() and not d.get("populated"):
            files = [p for p in directory.rglob("*") if p.is_file()]
            if files:
                fail(f"层 {name} 声明 populated: false，但目录里有 {len(files)} 个文件 —— "
                     f"要么翻成 true，要么把内容挪走")


def read_lines(rel: str) -> list:
    path = ROOT / rel
    if not path.is_file():
        fail(f"{rel} 不存在")
        return []
    return [l.strip() for l in path.read_text(encoding="utf-8", errors="replace").splitlines()
            if l.strip() and not l.strip().startswith("#")]


def parse_accesswidener(rel: str) -> set:
    """AW 行形如：accessible field <owner> <member> <desc>（命名空间在文件头的 named）。"""
    out = set()
    for line in read_lines(rel):
        parts = line.split()
        if len(parts) >= 4 and parts[0] == "accessible":
            out.add(f"{parts[2]} {parts[3]}")
    return out


def parse_accesstransformer(rel: str) -> set:
    """AT 行形如：public <owner.dotted> <member>。"""
    out = set()
    for line in read_lines(rel):
        parts = line.split()
        if len(parts) >= 3 and parts[0] in ("public", "public-f"):
            out.add(f"{parts[1]} {parts[2]}")
    return out


def check_access_parity(parity: dict) -> None:
    """三份访问扩宽文件必须覆盖同一批成员，各写自己家族的命名。

    判据（不需要映射表也能咬住真实的漂移）：
      · Fabric AW 的成员集合 == 声明表 yarn 侧
      · NeoForge AT 的成员集合 == 声明表非 null 的 official 侧
      · Forge AT 的**类名集合**与 NeoForge 相同、条数相同，成员名必须是 SRG 形状
      · NeoForge AT 的成员名不许是 SRG 形状（写错家族的常见翻车）
    """
    files = parity.get("files", {})
    members = parity.get("members", [])
    if not files or not members:
        fail("versions/access-parity.json 缺 files 或 members")
        return

    yarn = parse_accesswidener(files["yarn"])
    official = parse_accesstransformer(files["official"])
    srg = parse_accesstransformer(files["srg"])

    declared_yarn = {m["yarn"] for m in members}
    declared_official = {m["official"] for m in members if m.get("official")}

    for label, actual, declared in (("Fabric AW", yarn, declared_yarn),
                                    ("NeoForge AT", official, declared_official)):
        for missing in sorted(declared - actual):
            fail(f"{label} 里没有声明表写着的成员：{missing} —— 声明与文件不一致")
        for extra in sorted(actual - declared):
            fail(f"{label} 里有声明表没有的成员：{extra} —— "
                 f"把它写进 versions/access-parity.json，并给出另一侧的对应项或说明为什么不需要")

    srg_shape = re.compile(r"^f_\d+_$")
    for entry in sorted(official):
        if srg_shape.match(entry.split()[-1]):
            fail(f"NeoForge 的 AT 写了 SRG 名（{entry}）—— 那一侧要写官方名；SRG 名只在 Forge 那侧")

    official_owners = {e.split()[0] for e in official}
    srg_owners = {e.split()[0] for e in srg}
    if official_owners != srg_owners:
        fail(f"两份 AT 的类名集合不一致：NeoForge {sorted(official_owners)} vs Forge {sorted(srg_owners)}")
    if len(official) != len(srg):
        fail(f"两份 AT 条数不一致：NeoForge {len(official)} 条，Forge {len(srg)} 条")
    for entry in sorted(srg):
        if not srg_shape.match(entry.split()[-1]):
            fail(f"Forge 的 AT 写了非 SRG 名（{entry}）—— 那一侧运行时用 SRG 名，写官方名会静默不生效")


def check_aliases(aliases: dict) -> None:
    """别名必须指向真实存在的层内文件 —— 别名的价值在于它可检查，腐坏的别名会静默失效。"""
    for layer_rel, mapping in aliases.items():
        for key, value in mapping.items():
            if not (ROOT / layer_rel / key).is_file():
                fail(f"versions/mapping-aliases.json 里的 {layer_rel}/{key} 不存在 —— "
                     f"别名指向了一个没有的文件，这条对应关系已经腐坏")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix-out", help="把 CI 矩阵写成 JSON 到这个路径")
    args = parser.parse_args()

    targets_raw = load_json("versions/targets.json")
    layers_raw = load_json("versions/layers.json")
    load_json("versions/third-party-apis.json")
    aliases_raw = load_json("versions/mapping-aliases.json")
    parity_raw = load_json("versions/access-parity.json")
    targets = entries_of(targets_raw)
    layers = entries_of(layers_raw)
    aliases = {k: v for k, v in aliases_raw.items() if not k.startswith("_")}

    check_root_identity()
    check_layers(layers)
    check_aliases(aliases)
    check_access_parity(parity_raw)
    check_targets(targets, layers, aliases)

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
