package de.ronny.pololauncher;

/** Memory-safe inSampleSize calculation for user-selected wallpapers. */
final class ImageSampling {
    // ARGB_8888: 8 million pixels are ~32 MiB before allocator overhead.
    private static final long MAX_DECODE_PIXELS = 8_000_000L;

    private ImageSampling() {}

    static int forCenterCrop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0)
            return 1;

        int sample = 1;
        while (sample <= 128) {
            int next = sample * 2;
            int nextW = Math.max(1, sourceWidth / next);
            int nextH = Math.max(1, sourceHeight / next);
            long currentPixels = (long) Math.max(1, sourceWidth / sample)
                    * Math.max(1, sourceHeight / sample);

            boolean currentTooLarge = currentPixels > MAX_DECODE_PIXELS;
            boolean nextStillCoversTarget = nextW >= targetWidth && nextH >= targetHeight;
            if (!currentTooLarge && !nextStillCoversTarget) break;
            if (nextW <= 1 || nextH <= 1) break;
            sample = next;
        }
        return sample;
    }
}
