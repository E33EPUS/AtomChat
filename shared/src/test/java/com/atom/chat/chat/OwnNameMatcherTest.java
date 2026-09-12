package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Own-echo matching against the decorated names servers put on the wire
 * (2026-09-11: a {@code /team} prefix made own messages show up twice, because
 * the wire name {@code 123E33EPUS} was compared against the bare
 * {@code E33EPUS}).
 */
class OwnNameMatcherTest {
    private static final List<String> OWN =
            List.of("E33EPUS", "123E33EPUS", "[VIP] E33EPUS", "[VIP] E33EPUS [员]");

    @Test
    void bareProfileNameMatches() {
        assertTrue(OwnNameMatcher.matches("E33EPUS", OWN));
    }

    @Test
    void teamPrefixWithoutSeparatorMatches() {
        assertTrue(OwnNameMatcher.matches("123E33EPUS", OWN));
    }

    @Test
    void decoratedPrefixAndSuffixMatch() {
        assertTrue(OwnNameMatcher.matches("[VIP] E33EPUS", OWN));
        assertTrue(OwnNameMatcher.matches("[VIP] E33EPUS [员]", OWN));
    }

    @Test
    void colourCodesAndPaddingAreIgnored() {
        assertTrue(OwnNameMatcher.matches("§b123E33EPUS§r", OWN));
        assertTrue(OwnNameMatcher.matches("  123E33EPUS  ", OWN));
    }

    @Test
    void lookalikeNamesDoNotMatch() {
        // Suppression must stay exact: a longer name that merely contains ours
        // belongs to someone else and may never be swallowed.
        assertFalse(OwnNameMatcher.matches("NotE33EPUS", OWN));
        assertFalse(OwnNameMatcher.matches("123E33EPUSx", OWN));
        assertFalse(OwnNameMatcher.matches("33EPUS", OWN));
    }

    @Test
    void nullAndBlankAreSafe() {
        assertFalse(OwnNameMatcher.matches(null, OWN));
        assertFalse(OwnNameMatcher.matches("E33EPUS", null));
        assertFalse(OwnNameMatcher.matches("   ", OWN));
        assertFalse(OwnNameMatcher.matches("E33EPUS", Arrays.asList(null, " ", "")));
    }
}
