**Start from the symptom, then narrow it down with the log.** Everything here revolves around one file: `.minecraft/logs/latest.log` (on a server, `logs/latest.log`).

## The panel will not open / pressing T does nothing

1. **Is the jar the right one?** The Fabric build goes with Fabric Loader, the NeoForge build with NeoForge, the Forge build with 1.20.1 + Forge 47+; a jar for another loader will not load at all. Check the Java version too (21+ for Fabric / NeoForge, 17 for Forge 1.20.1).
2. **Is another screen in the way?** The `Y` key only brings the panel up when no other screen is open.
3. **Read the log:** search for `atomchat` and look for a load failure or a missing dependency (`fabric-api` is required on Fabric).
4. **Key conflict:** under `Options → Controls → Key Binds → AtomChat`, check that `Open AtomChat` (default `Y`) has not been taken by another mod; the vanilla chat keys (`T` / `/`) can also stop working when another mod rewrites them.

## The file picker will not open (image / avatar / wallpaper buttons do nothing)

This is a compatibility problem fixed in 0.2.9: some mods (in our testing, **TerraFirmaCraft / TFC**) initialise AWT very early, while Minecraft's client main class `Main` writes `java.awt.headless` to `true` — whoever touches AWT first locks that answer in. The result was AtomChat throwing `HeadlessException` when opening a file picker, with the log swallowing it, so the player saw a button that did nothing.

**Since 0.2.9** a mixin plugin claims AWT during the mixin preparation phase (before any mod is constructed and before `Main` runs). If it still cannot:

- the log gets an `ERROR` line naming the reason;
- the game shows a notice (`atomchat.picker.headless`).

Search the log for `HeadlessException`, `java.awt.headless` or `Image picker`. On 0.2.8 or earlier, **upgrading to 0.2.9 is the fix**.

## Messages render grey

Grey means the client cannot tell whether that line is a player message, so it plays safe and renders it as a system line (that is the **design**, not a mis-detection). It usually happens with nickname / title plugins that use a message format the client does not know.

The remedy is to describe the format by hand in `config/atomchat/atomchat-client.json` — `chatTemplates` for public chat and `whisperTemplates` for private chat, with the placeholders `{name}` `{display_name}` `{prefix}` `{suffix}` `{sep}` `{content}` (`{content}` must appear exactly once). **Reopen the chat screen** after editing; no game restart needed.

## Images

An image that will not show, an old image gone broken, a failed upload → see the troubleshooting table in [Media and Transfers](Media-and-Transfers).

## The panel goes fully black (old versions only)

Before 0.2.8, opening the panel on Forge 1.20.1 could turn the whole screen black (the game did not crash and typing still worked). The cause was the blur pre-pass writing raw GL state that disagreed with 1.20.1's cache-gated setters; **fixed in 0.2.8**. Since then, a failed blur pass falls back to an opaque solid panel and logs a throttled warning carrying the GL error code instead of going quietly black.

## Known unsupported

- **Linux / macOS:** the jar carries only Skija's Windows x64 native library, so the panel cannot start.
- **Quark:** the emote menu does not appear when Quark is installed; compatibility work is in progress.
- **Other MC versions / loaders:** currently Fabric and NeoForge 1.21.1, plus Forge 1.20.1.

## What to include in a bug report

1. The mod version (e.g. `v0.2.10`), your loader and its version, and your MC version;
2. whether this is **single-player / a LAN host / a dedicated server**, and whether the server also runs AtomChat;
3. reproduction steps — shorter is better: what you did → what you expected → what happened;
4. `.minecraft/logs/latest.log` (or a report from `crash-reports/`);
5. a screenshot or clip of the moment it went wrong (very useful for interface problems);
6. for anything image- or pack-related, paste the `Stored hosted media`, `Failed to upload image` and transfer-failure lines from the log.

Open an issue at [Issues](https://github.com/E33EPUS/AtomChat/issues).
