package com.atom.chat.text;

/**
 * Pure text measurement hook so layout can run without a live font/Skia context.
 */
@FunctionalInterface
public interface TextMeasurer {
    float measure(String text);
}
