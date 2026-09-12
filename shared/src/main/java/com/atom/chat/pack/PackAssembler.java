package com.atom.chat.pack;

/**
 * Reassembles one pack file from its chunks and refuses anything that does not
 * hash to the manifest value.
 *
 * <p>A chunk that would leave a hole, run past the declared size or hide a
 * wrong file can never reach the disk: the buffer only produces bytes when it is
 * complete <em>and</em> its SHA-256 matches the manifest entry (decision 21).
 */
public final class PackAssembler {
    private PackAssembler() {
    }

    /** One file under construction. */
    public static final class FileBuffer {
        private final ServerPack.FileEntry entry;
        private final byte[] data;
        private int received;

        public FileBuffer(ServerPack.FileEntry entry) {
            this.entry = entry;
            this.data = new byte[entry.size()];
        }

        public ServerPack.FileEntry entry() {
            return entry;
        }

        public int size() {
            return data.length;
        }

        public int received() {
            return received;
        }

        public boolean isComplete() {
            return received == data.length;
        }

        /**
         * Takes one chunk.
         *
         * @return false when the chunk cannot belong to this file: it would leave
         *         a hole, it runs past the declared size, or it carries no data
         * @apiNote a chunk that is entirely older than what we already have is
         *          accepted and ignored — duplicates are harmless, holes are not.
         */
        public boolean offer(int offset, byte[] chunk) {
            if (chunk == null || offset < 0 || offset + chunk.length > data.length) {
                return false;
            }
            if (offset > received) {
                return false;
            }
            int start = Math.max(offset, received);
            int length = offset + chunk.length - start;
            if (length <= 0) {
                return true;
            }
            System.arraycopy(chunk, start - offset, data, start, length);
            received = offset + chunk.length;
            return true;
        }

        /** @return the verified bytes once complete, or null when incomplete or mismatched */
        public byte[] verified() {
            if (!isComplete()) {
                return null;
            }
            return ServerPack.sha256Hex(data).equals(entry.sha256Hex()) ? data.clone() : null;
        }
    }
}
