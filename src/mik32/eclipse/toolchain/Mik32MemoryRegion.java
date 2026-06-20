package mik32.eclipse.toolchain;

public enum Mik32MemoryRegion {

    RAM("RAM", 16 * 1024, false),
    EEPROM("EEPROM", 8 * 1024, false),
    FLASH("Flash (SPIFI)", -1, true);

    public final String displayName;
    public final int sizeBytes;
    public final boolean unlimited;

    Mik32MemoryRegion(String displayName, int sizeBytes, boolean unlimited) {
        this.displayName = displayName;
        this.sizeBytes   = sizeBytes;
        this.unlimited   = unlimited;
    }

    public static boolean isRamConfig(String configName) {
        return containsIgnoreCase(configName, "ram");
    }

    public static Mik32MemoryRegion romRegionFor(String configName) {
        if (containsIgnoreCase(configName, "eeprom")) {
            return EEPROM;
        }
        return FLASH;
    }

    private static boolean containsIgnoreCase(String s, String keyword) {
        if (s == null) {
            return false;
        }
        return s.toLowerCase(java.util.Locale.ROOT).contains(keyword);
    }
}