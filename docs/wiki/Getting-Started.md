**Install it, then actually use the panel once.** About three minutes end to end.

## 1. Pick the right jar

Download the one that matches your platform from [Releases](https://github.com/E33EPUS/AtomChat/releases):

| Your game | Download this file | You also need |
|---|---|---|
| Minecraft 1.21.1 + Fabric | `atomchat-Fabric-1.21.1-x.y.z.jar` | Fabric Loader 0.16.0+ with Fabric API, Java 21+ |
| Minecraft 1.21.1 + NeoForge | `atomchat-NeoForge-1.21.1-x.y.z.jar` | NeoForge 21.1+, Java 21+ |
| Minecraft 1.20.1 + Forge | `atomchat-Forge-1.20.1-x.y.z.jar` | Forge 47+, Java 17 |

> ⚠️ The three platforms **do not interchange**: a jar built for another loader simply will not load. Note also the `-slim` file in the Forge downloads — it is for development and carries no bundled dependencies, so play with the one that has no `-slim` suffix.

## 2. Drop it into mods

Drag the jar into `.minecraft/mods/` (for a modpack instance, that instance's own `mods/` folder).

If you want the **server** to host images, sync avatars, or hand out an emote pack and quick phrases, put the same jar into the server's `mods/` as well. That step is optional: everything still works without it, images just go through a third-party image host. The server side is covered in the [Server Owner Guide](Server-Owner-Guide).

## 3. Open the panel

Once you are in a world:

- press the **chat key** (default `T` or `/`) — AtomChat takes over the vanilla chat screen and puts you straight into public chat input;
- or press **`Y`** (the default; change it under `Options → Controls → Key Binds → AtomChat`) — this opens the panel directly and restores the page you were on last.

The panel looks like this: the public channel and the system clock on top, the message list in the middle, the input bar at the bottom, and three tabs below that — **Chat / Profile / Settings**.

## 4. Try these five things first

1. **Send a message:** type, hit enter. The input grows once your text passes one line, and the up/down arrows still move the caret between lines.
2. **Send an image:** click the image button left of the input; or drag an image file straight into the game window; or copy an image and press `Ctrl+V`. All three upload first, then insert the image code into your draft, and you send it with enter once it looks right.
3. **Use an emote:** click the emote button. The panel has three tabs — `Emoji` / `Kaomoji` / `Stickers`. A click inserts; stickers upload first and insert into the draft, then the panel closes.
4. **Right-click a message:** copy it, or reply with a quote (the quote carries the original message's bubble). Image messages also get a "Save" entry that writes the original image to disk.
5. **Click an avatar:** a single click opens the player's profile page; a second click within 300 ms is a QQ-style "nudge"; right-clicking the avatar opens a menu — mention, private chat, teleport, block.

## 5. Make it look like yours

Click the **Settings** tab at the bottom. It is a Windows 11 style 2×2 tile home: **Appearance / Chat / Privacy & Blocking / About**.

Every option **applies immediately and is written to disk at once — no game restart**. Two to play with first:

- `Appearance → UI scale`, from x0.75 to x1.50, scales the whole interface (handy on a small laptop);
- `Appearance → Custom wallpaper` takes your own image, with the long edge scaled down to 1024 pixels.

For the rest of the appearance options, and the full list of config keys, see [Appearance and Config](Appearance-and-Config).

## Next

- Where images actually live, and why some of them go through an image host → [Media and Transfers](Media-and-Transfers)
- How to add or remove emotes, and where server-offered ones come from → [Emotes](Emotes)
- The panel will not open after installing / messages turned grey / a crash → [Troubleshooting](Troubleshooting)
