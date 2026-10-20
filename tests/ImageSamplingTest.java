package de.ronny.pololauncher;

public final class ImageSamplingTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(ImageSampling.forCenterCrop(1920, 1080, 1024, 600) == 1,
                "ordinary Full-HD wallpaper needs no forced downsample");
        check(ImageSampling.forCenterCrop(4032, 3024, 1024, 600) == 2,
                "12 MP camera image is reduced to a safe size");
        check(ImageSampling.forCenterCrop(12000, 8000, 1024, 600) >= 4,
                "very large image is heavily reduced");
        check(ImageSampling.forCenterCrop(100000, 1000, 1024, 600) >= 2,
                "extreme panorama is memory bounded");
        check(ImageSampling.forCenterCrop(0, 1000, 1024, 600) == 1,
                "invalid bounds keep safe fallback");
        System.out.println("ImageSamplingTest: all cases passed");
    }
}
