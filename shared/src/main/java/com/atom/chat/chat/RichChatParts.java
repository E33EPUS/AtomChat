package com.atom.chat.chat;

import com.atom.chat.text.RichText;

/**
 * Sliced styled sender and content parts of a decorated chat line.
 *
 * @param sender  the decorated player label, preserving original run styles
 * @param content the message body after separators, preserving original run styles
 */
public record RichChatParts(RichText sender, RichText content) {
}
