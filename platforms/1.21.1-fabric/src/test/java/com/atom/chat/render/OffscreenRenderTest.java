package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.QuickPhrasePanel;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offscreen render coverage for the panel.
 *
 * <p>Why this exists: the draw layer is the only code in this mod that can kill
 * the JVM outright. Skija's {@code Path.close()} releases the native object
 * rather than closing a contour, and it was called before {@code drawPath} in
 * {@code SettingsSectionPage.drawChevron} — see hs_err_pid53868, "Render
 * thread", a native frame with no module and no Java exception anywhere for a
 * try/catch to catch. Nothing rendered a Canvas in a test before this file, so
 * that whole class of mistake had no guard at all.
 *
 * <p>Every page and popup draws onto a CPU raster surface here: no GL, no
 * window, no game. Three properties are checkable from Java and are checked:
 * nothing throws, every {@code save()} is matched by exactly one
 * {@code restore()} (a leaked save is how the 0.2.4 notification banner leaked
 * its matrix), and the frame actually put pixels on the surface. The fourth
 * property is not an assertion — misusing a native handle takes the test JVM
 * down with it, which is the build going red, which is the point.
 *
 * <p>Surfaces draw more than one frame. A native object released during an
 * earlier frame is still reachable in the next one, so the repeat is what turns
 * "somebody freed a shared resource" from a silent corruption into a failure
 * here. The {@code AppIcons} paths are the live example: thirteen static
 * {@code Path} objects every icon draw borrows and nobody owns.
 *
 * <p>Scope: CPU raster only. {@code SkiaGraphics} renders onto Minecraft's
 * framebuffer through a GL backend render target, which needs a live client;
 * that path stays on the development-machine smoke run, and an offscreen pass
 * says nothing about it either way.
 */
class OffscreenRenderTest {
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;
    private static final int ACCENT = 0xFF3B82F6;

    @TempDir
    static Path platformRoot;

    /**
     * The config and emote paths the draw path reads come through {@link Platform},
     * which deliberately refuses to guess a value: a target that forgets to install
     * a provider is supposed to fail loudly, and a unit test that needs paths is
     * told to build its own. Installing one here is also what keeps this test
     * independent of whatever config the machine running it happens to have, and it
     * is the difference between the three targets running the same test and only
     * the two Forge-family ones getting far enough to reach the first colour token.
     */
    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /**
     * One raster surface shared by a test's frames, with the per-frame checks.
     * Reusing the surface is deliberate: an earlier frame's draw is what a later
     * frame would trip over.
     */
    private static final class Panel implements AutoCloseable {
        private final Surface surface;
        private final Canvas canvas;
        private final int baseline;

        private Panel() {
            surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
            canvas = surface.getCanvas();
            baseline = canvas.getSaveCount();
        }

        /** Balance only: some frames legitimately paint nothing (see the banner). */
        private void frame(String what, Consumer<Canvas> render) {
            canvas.clear(0);
            render.accept(canvas);
            assertEquals(baseline, canvas.getSaveCount(),
                    what + " left the canvas save stack unbalanced");
        }

        /** Balance, and the frame must have drawn something. */
        private void paintedFrame(String what, Consumer<Canvas> render) {
            frame(what, render);
            assertTrue(drawnBytes(surface) > 0, what + " painted nothing at all");
        }

        @Override
        public void close() {
            surface.close();
        }
    }

    /**
     * Non-zero bytes in the surface's pixels. The surface is cleared before every
     * frame, so this is zero only when the frame silently drew nothing — which
     * would make every other assertion in this file vacuous.
     */
    private static int drawnBytes(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            byte[] pixels = bitmap.readPixels();
            int nonZero = 0;
            for (byte b : pixels) {
                if (b != 0) {
                    nonZero++;
                }
            }
            return nonZero;
        } finally {
            bitmap.close();
        }
    }

    @Test
    void everySettingsSectionDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            SettingsSectionPage page = new SettingsSectionPage();
            for (int frame = 0; frame < 2; frame++) {
                for (SettingsSection section : SettingsSection.values()) {
                    panel.paintedFrame("settings section " + section,
                            canvas -> page.render(canvas, layout, section, -1.0F, -1.0F, 0.0F, ACCENT));
                }
            }
        }
    }

    /**
     * {@code drawChevron} is private and only runs for a foldable group label, so
     * if a future change drops those rows the test above keeps passing while
     * covering nothing. The feed is therefore asserted on its own.
     */
    @Test
    void theSettingsSectionsStillFeedTheFoldableLabelRows() {
        SettingsSectionPage page = new SettingsSectionPage();
        boolean anyLabel = false;
        for (SettingsSection section : SettingsSection.values()) {
            anyLabel |= page.rows(section).stream()
                    .anyMatch(row -> row.kind() == SettingsSectionPage.RowKind.LABEL);
        }
        assertTrue(anyLabel, "no settings section produces a group label row, so the chevron path is uncovered");
    }

    @Test
    void settingsHomePageDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.ofRoot(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            SettingsHomePage page = new SettingsHomePage();
            for (int frame = 0; frame < 2; frame++) {
                panel.paintedFrame("settings home", canvas -> page.render(canvas, layout, 120.0F, 300.0F, 0.0F));
            }
        }
    }

    @Test
    void messageListDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            List<ChatMessage> history = conversation();
            // History that arrived before the panel opened does not animate in,
            // which is also what makes this frame paint real pixels instead of
            // compositing an entrance layer at zero opacity.
            MessageListView view = new MessageListView(new OffscreenHost(openedAfter(history)));
            ScrollController scroll = new ScrollController();
            try {
                for (int frame = 0; frame < 2; frame++) {
                    panel.paintedFrame("message list", draw(view, layout, history, scroll));
                }
            } finally {
                view.dispose();
            }
        }
    }

    /**
     * The other half of the entrance rule: a message that arrives while the panel
     * is open wraps its row in {@code saveLayer} for the first frames. That is the
     * nesting the 0.2.4 banner leaked a matrix through, so the balance check runs
     * on the animated frame and the paint check runs once the animation is over.
     */
    @Test
    void aMessageArrivingWhileThePanelIsOpenSettlesAndPaints() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            List<ChatMessage> feed = conversation();
            MessageListView view = new MessageListView(new OffscreenHost(openedAfter(feed)));
            ScrollController scroll = new ScrollController();
            try {
                // Built after the open stamp, so it is strictly newer than the panel.
                feed.add(new ChatMessage(Text.literal("just arrived"), false));
                panel.frame("message list entrance", draw(view, layout, feed, scroll));
                waitOutEntranceAnimation();
                panel.paintedFrame("settled message list", draw(view, layout, feed, scroll));
            } finally {
                view.dispose();
            }
        }
    }

    @Test
    void emojiPanelDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            EmojiPanel panelUi = new EmojiPanel(new OffscreenEmojiHost());
            panelUi.toggle();
            for (int frame = 0; frame < 2; frame++) {
                panelUi.update(200L);
                panel.paintedFrame("emoji panel",
                        canvas -> panelUi.render(canvas, layout, -1.0F, -1.0F, 200L));
            }
        }
    }

    @Test
    void quickPhrasePanelDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            QuickPhrasePanel panelUi = new QuickPhrasePanel(text -> {
            }, () -> ACCENT);
            panelUi.open();
            for (int frame = 0; frame < 2; frame++) {
                panelUi.update(200L);
                panel.paintedFrame("quick phrase panel",
                        canvas -> panelUi.render(canvas, layout, -1.0F, -1.0F, 200L));
            }
        }
    }

    @Test
    void notificationBannerDrawsAndBalancesTheCanvas() {
        try (Panel panel = new Panel()) {
            ChatMessage message = new ChatMessage(Text.literal("you were mentioned"), false);
            NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.MENTION, "Alice",
                    "you were mentioned", message);
            waitOutEntranceAnimation();
            for (int frame = 0; frame < 2; frame++) {
                panel.paintedFrame("notification banner", canvas -> NotificationBanner.INSTANCE
                        .renderInPanel(canvas, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 120.0F, 160.0F));
            }
        }
    }

    private static Consumer<Canvas> draw(MessageListView view, UiLayout layout, List<ChatMessage> messages,
                                         ScrollController scroll) {
        return canvas -> view.draw(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(),
                messages, scroll);
    }

    /**
     * A screen-open stamp strictly after every message in the feed, so the feed
     * counts as "arrived before the panel opened" without depending on two
     * {@code currentTimeMillis()} calls landing in different milliseconds.
     */
    private static long openedAfter(List<ChatMessage> messages) {
        return messages.stream().mapToLong(ChatMessage::getTimestamp).max().orElse(0L) + 1L;
    }

    /** Banners and arriving messages fade in over {@link UiMotion#MESSAGE_MS}. */
    private static void waitOutEntranceAnimation() {
        try {
            Thread.sleep(UiMotion.MESSAGE_MS * 2L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting out the entrance animation", e);
        }
    }

    /** Own, other, system, quoted, duplicated and wrapping rows in one feed. */
    private static List<ChatMessage> conversation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(Text.literal("Alice"), false));
        messages.add(new ChatMessage(Text.literal("a plain line from someone else"), false));
        messages.add(new ChatMessage(Text.literal("my own line"), true));
        messages.add(new ChatMessage(Text.literal(
                "a line long enough that the bubble has to wrap it onto a second row instead of "
                        + "running off the edge of the panel"), false));
        messages.add(new ChatMessage(Text.literal("a system notice"), false, true));
        messages.add(new ChatMessage(Text.literal("the reply body"), true, "Bob", "the line being quoted"));
        messages.add(new ChatMessage(Text.literal("the same line again"), false).withDuplicateCount(3));
        return messages;
    }

    /** The screen-provided answers the message list needs while drawing. */
    private static final class OffscreenHost implements MessageListView.Host {
        private final UUID own = UUID.nameUUIDFromBytes("offscreen-own".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        private final long openStart;

        private OffscreenHost(long openStart) {
            this.openStart = openStart;
        }

        @Override
        public UUID ownUuid() {
            return own;
        }

        @Override
        public String ownName() {
            return "Offscreen";
        }

        @Override
        public String senderName(ChatMessage message) {
            return message.isOwn() ? "Offscreen" : "Alice";
        }

        @Override
        public long openStart() {
            return openStart;
        }
    }

    /** The composer side effects the emoji panel expects the screen to own. */
    private static final class OffscreenEmojiHost implements EmojiPanel.Host {
        @Override
        public void insert(String text) {
        }

        @Override
        public void sendSticker(Path file) {
        }

        @Override
        public void pickEmoteFile() {
        }
    }
}
