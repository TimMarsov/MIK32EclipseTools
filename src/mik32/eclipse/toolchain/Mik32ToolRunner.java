package mik32.eclipse.toolchain;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Platform;

public final class Mik32ToolRunner {

    private static final String TOOL_PREFIX = "riscv-none-elf-";

    private Mik32ToolRunner() {
    }

    public static void objcopy(File elfFile, File hexFile, File workDir)
            throws IOException, InterruptedException, Mik32ToolException {
        List<String> command = new ArrayList<>();
        command.add(toolPath("objcopy"));
        command.add("-O");
        command.add("ihex");
        command.add(elfFile.getAbsolutePath());
        command.add(hexFile.getAbsolutePath());
        run(command, workDir, null);
    }

    public static Mik32SizeResult size(File elfFile, File workDir)
            throws IOException, InterruptedException, Mik32ToolException {
        List<String> command = new ArrayList<>();
        command.add(toolPath("size"));
        command.add(elfFile.getAbsolutePath());

        StringBuilder output = new StringBuilder();
        run(command, workDir, output);

        for (String line : output.toString().split("\\r?\\n")) {
            Mik32SizeResult result = Mik32SizeResult.parse(line);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    static String launchToolPath(String toolSuffix) {
        return toolPath(toolSuffix);
    }

    private static String toolPath(String toolSuffix) {
        String name = toolName(toolSuffix);
        String configuredPath = Mik32PluginPreferences.getToolchainPath();
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            return new File(configuredPath.trim(), name).getAbsolutePath();
        }
        try {
            File eclipseHome = new File(Platform.getInstallLocation().getURL().toURI());
            File executable = new File(eclipseHome, "../riscv-gcc/bin/" + name).getCanonicalFile();
            if (executable.isFile()) {
                return executable.getAbsolutePath();
            }
        } catch (IOException | URISyntaxException | RuntimeException exception) {
        }
        return name;
    }

    private static String toolName(String toolSuffix) {
        String name = TOOL_PREFIX + toolSuffix;
        if (isWindows()) {
            name += ".exe";
        }
        return name;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void run(List<String> command, File workDir, StringBuilder stdout)
            throws IOException, InterruptedException, Mik32ToolException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(stdout == null);
        if (stdout != null) {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        Process process = pb.start();

        if (stdout != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append('\n');
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Mik32ToolException(command.get(0) + " exited with code " + exitCode);
        }
    }

    public static final class Mik32ToolException extends Exception {
        public Mik32ToolException(String message) {
            super(message);
        }
    }
}
