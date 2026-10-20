package de.ronny.pololauncher;

public final class LocalityTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        Locality chemnitz = new Locality("Chemnitz", 50.83, 12.92, now);
        check(chemnitz.usable(), "valid locality rejected");
        check(Locality.sameName(chemnitz,
                new Locality("  CHEMNITZ  ", 50.84, 12.93, now)), "name comparison");
        check(!Locality.sameName(chemnitz,
                new Locality("Flöha", 50.86, 13.07, now)), "different towns merged");
        check(!new Locality("", 50.83, 12.92, now).usable(), "empty name accepted");
        check(!new Locality("Ort", 91d, 12.92, now).usable(), "invalid latitude accepted");
        System.out.println("LocalityTest passed");
    }
}
