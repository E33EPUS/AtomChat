package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarCompanionServerTest {
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void pngMagicIsAccepted() {
        byte[] data = new byte[PNG_HEADER.length + 16];
        System.arraycopy(PNG_HEADER, 0, data, 0, PNG_HEADER.length);
        assertTrue(AvatarCompanionServer.isPng(data));
    }

    @Test
    void nonPngPayloadsAreRejected() {
        assertFalse(AvatarCompanionServer.isPng("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(AvatarCompanionServer.isPng(new byte[0]));
        assertFalse(AvatarCompanionServer.isPng("<html>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void truncatedMagicIsRejected() {
        byte[] data = new byte[PNG_HEADER.length - 1];
        System.arraycopy(PNG_HEADER, 0, data, 0, data.length);
        assertFalse(AvatarCompanionServer.isPng(data));
    }

    @Test
    void payloadIdsAreStable() {
        // Channel names are wire contracts: never rename these.
        assertTrue(AvatarPayloads.AvatarUploadPayload.ID.id().getPath().equals("avatar_upload"));
        assertTrue(AvatarPayloads.AvatarRequestPayload.ID.id().getPath().equals("avatar_request"));
        assertTrue(AvatarPayloads.AvatarDataPayload.ID.id().getPath().equals("avatar_data"));
        assertTrue(AvatarPayloads.AvatarUploadPayload.ID.id().getNamespace().equals("atomchat"));
    }

    @Test
    void maxAvatarBytesIsLargeEnoughForCroppedPng() {
        // The cropper emits a 256px PNG, typically well under the cap.
        assertTrue(AvatarPayloads.MAX_AVATAR_BYTES >= 256 * 1024);
        assertTrue(UUID.fromString("00000000-0000-0000-0000-000000000000") != null);
    }
}
