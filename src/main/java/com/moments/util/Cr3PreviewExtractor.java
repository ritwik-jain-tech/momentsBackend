package com.moments.util;

/**
 * Extracts an embedded JPEG preview from a Canon CR3 (ISO-BMFF / "crx") RAW file.
 *
 * <p>Browsers cannot render CR3 directly, so moments created from a CR3 upload would have no
 * displayable image until the Python face-tagging pipeline produces optimized derivatives. CR3
 * files embed one or more standard JPEG previews (a small {@code THMB} thumbnail and a larger
 * {@code PRVW} preview) inside the {@code moov} box near the start of the file. Rather than parse
 * the full CRX box tree, we scan the leading bytes for JPEG {@code SOI..EOI} segments and return
 * the largest one — that is the full-size preview suitable for a feed image.</p>
 */
public final class Cr3PreviewExtractor {

    /**
     * The embedded previews live in the {@code moov} box before the raw {@code mdat} payload, so we
     * only need the leading portion of the file. Capping the scan bounds memory for large RAWs.
     */
    public static final int DEFAULT_SCAN_LIMIT_BYTES = 16 * 1024 * 1024;

    /** A JPEG smaller than this is almost certainly the tiny THMB thumbnail, not the preview. */
    private static final int MIN_PREVIEW_BYTES = 8 * 1024;

    private Cr3PreviewExtractor() {
    }

    /**
     * @return {@code true} if the bytes start with an ISO-BMFF {@code ftyp} box whose major brand is
     *         {@code crx } (Canon CR3). Used when a filename/extension is unavailable.
     */
    public static boolean looksLikeCr3(byte[] data) {
        if (data == null || data.length < 16) {
            return false;
        }
        return data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p'
                && data[8] == 'c' && data[9] == 'r' && data[10] == 'x' && data[11] == ' ';
    }

    /**
     * Scans {@code data} for embedded JPEG segments and returns the largest one (the full-size
     * preview), or {@code null} if no usable JPEG preview is found.
     */
    public static byte[] extractLargestJpeg(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        int limit = Math.min(data.length, DEFAULT_SCAN_LIMIT_BYTES);

        int bestStart = -1;
        int bestEnd = -1;
        int i = 0;
        while (i < limit - 3) {
            // JPEG SOI: FF D8 FF
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8 && (data[i + 2] & 0xFF) == 0xFF) {
                int end = findJpegEnd(data, i + 2, limit);
                if (end > 0) {
                    int size = end - i;
                    if (size > (bestEnd - bestStart)) {
                        bestStart = i;
                        bestEnd = end;
                    }
                    i = end; // continue scanning after this JPEG
                    continue;
                }
            }
            i++;
        }

        if (bestStart < 0 || (bestEnd - bestStart) < MIN_PREVIEW_BYTES) {
            return null;
        }
        byte[] out = new byte[bestEnd - bestStart];
        System.arraycopy(data, bestStart, out, 0, out.length);
        return out;
    }

    /**
     * Returns the index just past the JPEG {@code EOI} (FF D9) marker starting the search at
     * {@code from}, or {@code -1} if no terminator is found within {@code limit}.
     */
    private static int findJpegEnd(byte[] data, int from, int limit) {
        for (int i = from; i < limit - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                return i + 2;
            }
        }
        return -1;
    }
}
