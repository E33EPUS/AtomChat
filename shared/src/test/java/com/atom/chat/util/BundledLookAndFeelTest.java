package com.atom.chat.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * Guards the FlatLaf relocation.
 *
 * <p>FlatLaf lives under a global package name, so another mod bundling it can
 * win the class loading race — that happened in the field (a mod called Mcpatch
 * shipped its own copy; our image picker then ran on it and Forge's class
 * transformer reported a class that copy did not have). Our copy is now bundled
 * under a private package, and these assertions fail if someone puts the public
 * dependency back or ships an unrelocated jar.
 */
class BundledLookAndFeelTest {

    private static final String SHADED = "com.atom.chat.shaded.flatlaf";
    private static final String PUBLIC = "com.formdev.flatlaf";

    @Test
    void theRelocatedLookAndFeelIsOnTheClasspath() {
        assertDoesNotThrow(() -> Class.forName(SHADED + ".FlatLightLaf"));
    }

    @Test
    void thePublicPackageIsNotBundled() {
        // If this ever passes, a public FlatLaf is back on the classpath — the
        // exact collision the relocation exists to prevent.
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(PUBLIC + ".FlatLightLaf"));
    }

    @Test
    void everyDelegateTheChooserNeedsResolves() {
        // FlatTableUI$StartEditingAction is the class the field failure surfaced
        // on: its superclass must be relocatable along with everything else.
        assertDoesNotThrow(() -> Class.forName(SHADED + ".ui.FlatUIAction"));
        assertDoesNotThrow(() -> Class.forName(SHADED + ".ui.FlatTableUI"));
        assertDoesNotThrow(() -> Class.forName(SHADED + ".ui.FlatTableUI$StartEditingAction"));
        assertDoesNotThrow(() -> Class.forName(SHADED + ".ui.FlatFileChooserUI"));
    }

    @Test
    void theLookAndFeelInstalls() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "a look and feel cannot be installed without a display");
        Class<?> laf = Class.forName(SHADED + ".FlatLightLaf");
        laf.getMethod("setup").invoke(null);
        assertEquals(SHADED + ".FlatLightLaf", UIManager.getLookAndFeel().getClass().getName());
    }
}
