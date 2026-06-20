package mik32.eclipse.toolchain;

import java.io.File;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class Startup implements IStartup {
    public static final String PLUGIN_ID = "mik32.eclipse.toolchain";

    @Override
    public void earlyStartup() {
        Bundle bundle = FrameworkUtil.getBundle(Startup.class);
        Platform.getLog(bundle).log(new Status(IStatus.INFO, PLUGIN_ID, "MrMarsov MIK32 Eclipse Tools loaded."));
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                new ElfChangeListener(),
                IResourceChangeEvent.POST_BUILD);
        Job.create("Update MIK32 launch configurations", monitor -> {
            try {
                Mik32ProjectWizard.updateLaunchConfigurationsForOpenProjects(monitor);
            } catch (CoreException exception) {
                log(IStatus.WARNING, "Failed to update MIK32 launch configurations", exception);
            }
            return Status.OK_STATUS;
        }).schedule();
    }

    private static final class ElfChangeListener implements IResourceChangeListener {
        @Override
        public void resourceChanged(IResourceChangeEvent event) {
            IResourceDelta delta = event.getDelta();
            if (delta == null) {
                return;
            }
            try {
                delta.accept(new IResourceDeltaVisitor() {
                    @Override
                    public boolean visit(IResourceDelta d) throws CoreException {
                        IResource resource = d.getResource();
                        if (resource.getType() != IResource.FILE) {
                            return true;
                        }
                        if ((d.getKind() & (IResourceDelta.ADDED | IResourceDelta.CHANGED)) == 0) {
                            return false;
                        }
                        if (!"elf".equals(resource.getFileExtension())) {
                            return false;
                        }
                        IProject project = resource.getProject();
                        if (!isMik32Project(project)) {
                            return false;
                        }
                        schedulePostBuild((IFile) resource, project);
                        return false;
                    }
                });
            } catch (CoreException e) {
                log(IStatus.ERROR, "MIK32 resource listener error", e);
            }
        }

        private static boolean isMik32Project(IProject project) {
            if (project == null || !project.isOpen()) {
                return false;
            }
            if (project.getFile(".mik32project").exists()) {
                return true;
            }
            try {
                ICProjectDescription pd = CoreModel.getDefault()
                        .getProjectDescription(project, false);
                if (pd == null) {
                    return false;
                }
                ICConfigurationDescription active = pd.getActiveConfiguration();
                if (active == null) {
                    return false;
                }
                String configId = active.getId();
                return configId != null
                        && configId.contains("gnumcueclipse.managedbuild.cross.riscv");
            } catch (Exception e) {
                return false;
            }
        }

        private static void schedulePostBuild(IFile elfResource, IProject project) {
            String configName = elfResource.getParent().getName();
            File elfFile = elfResource.getLocation().toFile();
            File outputDir = elfFile.getParentFile();
            String artifactBaseName = elfResource.getName()
                    .substring(0, elfResource.getName().length() - 4);
            File hexFile = new File(outputDir, artifactBaseName + ".hex");

            Job job = Job.create("MIK32 post-build: " + project.getName(), monitor -> {
                MessageConsole console = findOrCreateConsole("MIK32 Build");
                console.clearConsole();
                try (MessageConsoleStream stream = console.newMessageStream()) {
                    ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);

                    stream.println("Generating " + hexFile.getName() + " ...");
                    try {
                        Mik32ToolRunner.objcopy(elfFile, hexFile, outputDir);
                        stream.println("  OK: " + hexFile.getAbsolutePath());
                    } catch (Exception ex) {
                        stream.println("  ERROR (objcopy): " + ex.getMessage());
                        log(IStatus.ERROR, "objcopy failed", ex);
                        return org.eclipse.core.runtime.Status.OK_STATUS;
                    }

                    try {
                        Mik32SizeResult sizeResult = Mik32ToolRunner.size(elfFile, outputDir);
                        if (sizeResult != null) {
                            printMemoryReport(stream, sizeResult, configName);
                        } else {
                            stream.println("(could not parse size output)");
                        }
                    } catch (Exception ex) {
                        stream.println("  WARNING (size): " + ex.getMessage());
                        log(IStatus.WARNING, "size failed", ex);
                    }

                    try {
                        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
                    } catch (CoreException ex) {
                        log(IStatus.WARNING, "refresh failed", ex);
                    }

                } catch (java.io.IOException ex) {
                    log(IStatus.ERROR, "Console stream error", ex);
                }
                return org.eclipse.core.runtime.Status.OK_STATUS;
            });
            job.setSystem(false);
            job.setUser(false);
            job.schedule(200);
        }
    }

    private static void printMemoryReport(MessageConsoleStream stream,
            Mik32SizeResult size, String configName) throws java.io.IOException {
        stream.println("");
        stream.println("=== MIK32 Memory Usage (" + configName + ") ===");
        stream.println("");

        if (Mik32MemoryRegion.isRamConfig(configName)) {
            long totalRamUsed = size.text + size.data + size.bss;
            stream.println(formatRegionLine("RAM", totalRamUsed, Mik32MemoryRegion.RAM.sizeBytes));
        } else {
            Mik32MemoryRegion romRegion = Mik32MemoryRegion.romRegionFor(configName);
            stream.println(formatRegionLine(romRegion.displayName, size.romUsed(),
                    romRegion.unlimited ? -1 : romRegion.sizeBytes));
            stream.println(formatRegionLine("RAM", size.ramUsed(), Mik32MemoryRegion.RAM.sizeBytes));
        }

        stream.println("");
        stream.println("  text=" + size.text + "  data=" + size.data + "  bss=" + size.bss);
        stream.println("");
    }

    private static String formatRegionLine(String regionName, long used, long total) {
        String usedStr = String.format("%,d", used);
        if (total <= 0) {
            return String.format("  %-18s %s bytes  (no size limit)", regionName + ":", usedStr);
        }
        double pct = (double) used / total * 100.0;
        String bar = progressBar(pct, 20);
        String overflow = used > total ? "  OVERFLOW" : "";
        return String.format("  %-18s %s / %,d bytes  [%s]  %5.1f%%%s",
                regionName + ":", usedStr, total, bar, pct, overflow);
    }

    private static String progressBar(double pct, int width) {
        int filled = (int) Math.min(Math.round(pct / 100.0 * width), width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        return sb.toString();
    }

    static MessageConsole findOrCreateConsole(String name) {
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        for (IConsole console : manager.getConsoles()) {
            if (name.equals(console.getName()) && console instanceof MessageConsole mc) {
                return mc;
            }
        }
        MessageConsole console = new MessageConsole(name, null);
        manager.addConsoles(new IConsole[] { console });
        return console;
    }

    static void log(int severity, String message, Throwable cause) {
        Bundle bundle = FrameworkUtil.getBundle(Startup.class);
        Platform.getLog(bundle).log(new Status(severity, PLUGIN_ID, message, cause));
    }
}
