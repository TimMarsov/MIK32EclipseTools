package mik32.eclipse.toolchain;

import org.eclipse.core.runtime.Platform;

public final class Mik32PluginPreferences {
    public static final String FRAMEWORK_PATH = "frameworkPath";
    public static final String EXAMPLES_PATH = "examplesPath";
    public static final String UPLOADER_PATH = "uploaderPath";
    public static final String PROGRAMMER_CONFIG = "programmerConfig";

    private Mik32PluginPreferences() {
    }

    public static String getFrameworkPath() {
        return get(FRAMEWORK_PATH);
    }

    public static String getExamplesPath() {
        return get(EXAMPLES_PATH);
    }

    public static String getUploaderPath() {
        return get(UPLOADER_PATH);
    }

    public static String getProgrammerConfig() {
        return get(PROGRAMMER_CONFIG);
    }

    private static String get(String key) {
        return Platform.getPreferencesService().getString(Startup.PLUGIN_ID, key, "", null);
    }
}
