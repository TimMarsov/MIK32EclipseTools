package mik32.eclipse.toolchain;

public final class Mik32SizeResult {

    public final long text;
    public final long data;
    public final long bss;

    public Mik32SizeResult(long text, long data, long bss) {
        this.text = text;
        this.data = data;
        this.bss = bss;
    }

    public long romUsed() {
        return text + data;
    }

    public long ramUsed() {
        return data + bss;
    }

    public static Mik32SizeResult parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("text")) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            long text = Long.parseLong(parts[0]);
            long data = Long.parseLong(parts[1]);
            long bss  = Long.parseLong(parts[2]);
            return new Mik32SizeResult(text, data, bss);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
