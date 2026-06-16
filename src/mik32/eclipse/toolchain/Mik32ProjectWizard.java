package mik32.eclipse.toolchain;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.cdt.core.CCProjectNature;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.IProjectType;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedCProjectNature;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class Mik32ProjectWizard extends Wizard implements INewWizard {
    public static final String ID = "mik32.eclipse.toolchain.wizards.mik32Project";
    private static final String GENERATED_LAUNCH_ATTRIBUTE = "mik32.eclipse.toolchain.generatedLaunch";
    private static final String CONFIGURATION_ID_PREFIX = "mik32.eclipse.toolchain.configuration.";
    private static final String EEPROM_CONFIGURATION = "MIK32 EEPROM";
    private static final String FLASH_CONFIGURATION = "MIK32 Flash";
    private static final String RAM_CONFIGURATION = "MIK32 RAM";

    public enum ProjectMode {
        MANAGED("MIK32 Project", "managed"),
        CMAKE("MIK32 CMake Project", "cmake");

        private final String title;
        private final String buildSystem;

        ProjectMode(String title, String buildSystem) {
            this.title = title;
            this.buildSystem = buildSystem;
        }
    }

    private final ProjectMode mode;
    private Mik32ProjectPage projectPage;

    public Mik32ProjectWizard() {
        this(ProjectMode.MANAGED);
    }

    public Mik32ProjectWizard(ProjectMode mode) {
        this.mode = mode;
        setWindowTitle(mode.title);
        setNeedsProgressMonitor(true);
    }

    @Override
    public void init(IWorkbench workbench, org.eclipse.jface.viewers.IStructuredSelection selection) {
    }

    @Override
    public void addPages() {
        projectPage = new Mik32ProjectPage(mode.title);
        addPage(projectPage);
    }

    @Override
    public boolean performFinish() {
        try {
            getContainer().run(false, true, monitor -> {
                try {
                    createProject(monitor);
                } catch (Exception exception) {
                    throw new InvocationTargetException(exception);
                }
            });
            return true;
        } catch (Exception exception) {
            Throwable cause = unwrap(exception);
            MessageDialog.openError(getShell(), "MIK32 Project",
                    "Failed to create MIK32 project: " + cause.getMessage());
            log(cause);
            return false;
        }
    }

    private void createProject(IProgressMonitor monitor) throws Exception {
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        monitor.beginTask("Creating MIK32 project", 100);

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProject project = workspace.getRoot().getProject(projectPage.getProjectName());
        IProjectDescription description = workspace.newProjectDescription(project.getName());
        if (!projectPage.useDefaultLocation()) {
            description.setLocationURI(new File(projectPage.getLocation(), project.getName()).toURI());
        }

        project.create(description, monitor);
        project.open(monitor);

        copyTemplateFiles(project, monitor);
        ensureStarterFiles(project, monitor);
        if (projectPage.isLinkFramework()) {
            linkFramework(project, monitor);
        } else {
            copyFramework(project, monitor);
        }
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        configureManagedProject(project, monitor, projectPage.isLinkFramework(), projectPage.isCpp());
        createCMakeLists(project, monitor);
        createMetadataFile(project, monitor);
        createLaunchConfigurations(project, monitor);
        monitor.done();
    }

    static void configureManagedProject(IProject project, IProgressMonitor monitor) throws Exception {
        configureManagedProject(project, monitor, false, false);
    }

    static void configureManagedProject(IProject project, IProgressMonitor monitor, boolean linkedFramework) throws Exception {
        configureManagedProject(project, monitor, linkedFramework, false);
    }

    static void configureManagedProject(IProject project, IProgressMonitor monitor, boolean linkedFramework,
            boolean cppProject) throws Exception {
        CCorePlugin.getDefault().convertProjectToNewC(
            project,
            "org.eclipse.cdt.managedbuilder.core.configurationDataProvider",
            monitor
        );
        if (cppProject) {
            CCProjectNature.addCCNature(project, monitor);
        }
        ManagedCProjectNature.addManagedNature(project, monitor);
        ManagedCProjectNature.addManagedBuilder(project, monitor);
        ensureBuilder(project, "org.eclipse.cdt.managedbuilder.core.ScannerConfigBuilder", monitor);

        IProjectDescription desc = project.getDescription();
        String[] natures = desc.getNatureIds();
        boolean hasScannerNature = false;
        for (String nature : natures) {
            if ("org.eclipse.cdt.managedbuilder.core.ScannerConfigNature".equals(nature)) {
                hasScannerNature = true;
                break;
            }
        }
        if (!hasScannerNature) {
            String[] newNatures = new String[natures.length + 1];
            System.arraycopy(natures, 0, newNatures, 0, natures.length);
            newNatures[natures.length] = "org.eclipse.cdt.managedbuilder.core.ScannerConfigNature";
            desc.setNatureIds(newNatures);
            project.setDescription(desc, monitor);
        }

        IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(project);
        IProjectType projectType = ManagedBuildManager.getProjectType("mik32.eclipse.toolchain.projectType.executable");
        if (projectType != null && info != null) {
            IManagedProject managedProject = ManagedBuildManager.createManagedProject(project, projectType);
            info.setManagedProject(managedProject);
            
            for (IConfiguration existingConfig : managedProject.getConfigurations()) {
                managedProject.removeConfiguration(existingConfig.getId());
            }
            
            for (IConfiguration config : projectType.getConfigurations()) {
                if (isUserBuildConfiguration(config)) {
                    createBuildConfiguration(project, managedProject, config, linkedFramework);
                }
            }
            
            if (managedProject.getConfigurations().length > 0) {
                IConfiguration configuration = managedProject.getConfigurations()[0];
                ManagedBuildManager.setDefaultConfiguration(project, configuration);
                ManagedBuildManager.setSelectedConfiguration(project, configuration);
            }
            ManagedBuildManager.saveBuildInfo(project, true);
            ManagedBuildManager.updateCoreSettings(project);
            removeExtraCdtConfigurations(project, monitor);
        }
    }

    private static boolean isUserBuildConfiguration(IConfiguration config) {
        String id = config.getId();
        String name = config.getName();
        return id != null && id.startsWith(CONFIGURATION_ID_PREFIX)
                && isMik32ConfigurationName(name);
    }

    private static boolean isMik32ConfigurationName(String name) {
        return EEPROM_CONFIGURATION.equals(name)
                || FLASH_CONFIGURATION.equals(name)
                || RAM_CONFIGURATION.equals(name);
    }

    private static void removeExtraCdtConfigurations(IProject project, IProgressMonitor monitor) throws CoreException {
        ICProjectDescription description = CoreModel.getDefault().getProjectDescription(project, true);
        if (description == null) {
            return;
        }

        ICConfigurationDescription fallback = null;
        for (ICConfigurationDescription configuration : description.getConfigurations()) {
            if (isMik32ConfigurationName(configuration.getName())) {
                fallback = configuration;
                break;
            }
        }
        if (fallback == null) {
            return;
        }

        description.setActiveConfiguration(fallback);
        description.setDefaultSettingConfiguration(fallback);
        for (ICConfigurationDescription configuration : description.getConfigurations()) {
            if (!isMik32ConfigurationName(configuration.getName())) {
                description.removeConfiguration(configuration);
            }
        }
        CoreModel.getDefault().setProjectDescription(project, description, true, monitor);
    }

    private static void createBuildConfiguration(IProject project, IManagedProject managedProject, IConfiguration config,
            boolean linkedFramework) throws Exception {
        String name = config.getName();
        IConfiguration newConfig = managedProject.createConfiguration(config, config.getId() + "." + project.getName());
        newConfig.setArtifactName(project.getName());
        newConfig.setArtifactExtension("elf");
        useInternalBuilder(newConfig);
        
        IToolChain tc = newConfig.getToolChain();
        if (tc == null) {
            return;
        }
        
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.isa.base", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.arch.rv32i");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.isa.multiply", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.isa.compressed", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.isa.extensions", "_zicsr_zifencei");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.abi.integer", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.abi.integer.ilp32");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.level", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.level.size");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.messagelength", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.signedchar", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.functionsections", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.optimization.datasections", false);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.debugging.level", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.debugging.level.max");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.toolchain.id", "2273142913");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.toolchain.name", "xPack GNU RISC-V Embedded GCC");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.prefix", "riscv-none-elf-");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.c", "gcc");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.cpp", "g++");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.ar", "ar");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.objcopy", "objcopy");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.objdump", "objdump");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.size", "size");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.make", "make");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.command.rm", "rm");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.tune", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.tune.default");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.codemodel", "ilg.gnumcueclipse.managedbuild.cross.riscv.option.target.codemodel.low");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.warnings.allwarn", true);
        
        String frameworkRoot = getFrameworkRoot(linkedFramework);
        String[] includePaths = getIncludePaths(frameworkRoot);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.compiler.include.paths", includePaths);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.assembler.usepreprocessor", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.assembler.include.paths", includePaths);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.compiler.include.paths", includePaths);
        
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.nostart", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.gcsections", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.usenewlibnano", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.mapfilename", project.getName() + ".map");
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.nostart", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.gcsections", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.usenewlibnano", true);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.mapfilename", project.getName() + ".map");
        
        String[] libPaths = { frameworkRoot + "/shared/ldscripts" };
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.paths", libPaths);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.paths", libPaths);
        
        String linkerScript = frameworkRoot + "/shared/ldscripts/eeprom.ld";
        if (name.contains("Flash")) {
            linkerScript = frameworkRoot + "/shared/ldscripts/spifi.ld";
        } else if (name.contains("RAM")) {
            linkerScript = frameworkRoot + "/shared/ldscripts/ram.ld";
        }
        String[] scriptList = { linkerScript };
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.c.linker.scriptfile", scriptList);
        setOptionValue(newConfig, tc, "ilg.gnumcueclipse.managedbuild.cross.riscv.option.cpp.linker.scriptfile", scriptList);
    }

    static void linkFramework(IProject project, IProgressMonitor monitor) throws CoreException {
        IFolder frameworkFolder = project.getFolder("framework");
        String frameworkPath = Mik32PluginPreferences.getFrameworkPath();
        if (frameworkPath == null || frameworkPath.trim().isEmpty()) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, "Framework path is not configured"));
        }
        File frameworkDirectory = new File(frameworkPath);
        if (!frameworkDirectory.isDirectory()) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Framework path does not exist: " + frameworkPath));
        }
        if (frameworkFolder.exists()) {
            if (frameworkFolder.isLinked() && frameworkFolder.getLocation() != null
                    && isSameLocation(frameworkFolder.getLocation().toFile(), frameworkDirectory)) {
                return;
            }
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Project already contains a framework folder. Remove it or choose another project location."));
        }
        frameworkFolder.createLink(new Path(frameworkDirectory.getAbsolutePath()), IResource.REPLACE, monitor);
    }

    static void copyFramework(IProject project, IProgressMonitor monitor) throws Exception {
        IFolder frameworkFolder = project.getFolder("framework");
        if (frameworkFolder.exists()) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Project already contains a framework folder. Remove it or choose link mode."));
        }
        String frameworkPath = Mik32PluginPreferences.getFrameworkPath();
        if (frameworkPath == null || frameworkPath.trim().isEmpty()) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, "Framework path is not configured"));
        }
        java.nio.file.Path frameworkRoot = java.nio.file.Path.of(frameworkPath);
        if (!java.nio.file.Files.isDirectory(frameworkRoot)) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Framework path does not exist: " + frameworkPath));
        }
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(frameworkRoot)) {
            java.util.Iterator<java.nio.file.Path> iterator = stream.filter(java.nio.file.Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                java.nio.file.Path source = iterator.next();
                String relative = frameworkRoot.relativize(source).toString().replace('\\', '/');
                if (shouldSkipFrameworkFile(relative)) {
                    continue;
                }
                IFile target = project.getFile(new Path("framework/" + relative));
                ensureParentFolder(target.getParent(), monitor);
                try (InputStream input = java.nio.file.Files.newInputStream(source)) {
                    target.create(input, true, monitor);
                }
            }
        }
    }

    private static boolean shouldSkipFrameworkFile(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        String[] segments = lower.split("/");
        for (String segment : segments) {
            if (segment.equals(".git")
                    || segment.equals(".pio")
                    || segment.equals(".settings")
                    || segment.equals("debug")
                    || segment.equals("release")
                    || segment.equals("build")
                    || segment.startsWith("cmake-build")) {
                return true;
            }
        }
        return lower.endsWith(".o")
                || lower.endsWith(".obj")
                || lower.endsWith(".d")
                || lower.endsWith(".elf")
                || lower.endsWith(".map")
                || lower.endsWith(".bin")
                || lower.endsWith(".hex")
                || lower.endsWith(".lst")
                || lower.endsWith(".list");
    }

    private static boolean isSameLocation(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (java.io.IOException exception) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
    }

    static void ensureFrameworkLinked(IProject project, IProgressMonitor monitor) throws CoreException {
        IFolder frameworkFolder = project.getFolder("framework");
        if (frameworkFolder.exists() && frameworkFolder.isLinked()) {
            return;
        }
        linkFramework(project, monitor);
    }

    private static void ensureBuilder(IProject project, String builderId, IProgressMonitor monitor) throws CoreException {
        IProjectDescription description = project.getDescription();
        for (ICommand command : description.getBuildSpec()) {
            if (builderId.equals(command.getBuilderName())) {
                return;
            }
        }
        ICommand command = description.newCommand();
        command.setBuilderName(builderId);
        ICommand[] oldCommands = description.getBuildSpec();
        ICommand[] newCommands = new ICommand[oldCommands.length + 1];
        System.arraycopy(oldCommands, 0, newCommands, 0, oldCommands.length);
        newCommands[oldCommands.length] = command;
        description.setBuildSpec(newCommands);
        project.setDescription(description, monitor);
    }

    private static String getFrameworkRoot(boolean linkedFramework) {
        if (!linkedFramework) {
            return "../framework";
        }
        String frameworkPath = Mik32PluginPreferences.getFrameworkPath();
        if (frameworkPath == null || frameworkPath.trim().isEmpty()) {
            return "../framework";
        }
        return frameworkPath.trim().replace('\\', '/');
    }

    private static String[] getIncludePaths(String frameworkRoot) {
        return new String[] {
            "..",
            "../Inc",
            "../Src",
            frameworkRoot + "/shared/include",
            frameworkRoot + "/shared/periphery",
            frameworkRoot + "/shared/runtime",
            frameworkRoot + "/shared/libs",
            frameworkRoot + "/hal/core/Include",
            frameworkRoot + "/hal/peripherals/Include",
            frameworkRoot + "/hal/utilities/Include"
        };
    }

    private void copyTemplateFiles(IProject project, IProgressMonitor monitor) throws Exception {
        Bundle bundle = FrameworkUtil.getBundle(Mik32ProjectWizard.class);
        Enumeration<URL> entries = bundle.findEntries("templates/baremetal/files", "*", true);
        if (entries == null) {
            return;
        }

        while (entries.hasMoreElements()) {
            URL entry = entries.nextElement();
            String path = entry.getPath().replace('\\', '/');
            String marker = "/templates/baremetal/files/";
            int markerIndex = path.indexOf(marker);
            if (markerIndex < 0 || path.endsWith("/")) {
                continue;
            }

            String relativePath = path.substring(markerIndex + marker.length());
            if ("Makefile".equalsIgnoreCase(relativePath)) {
                continue;
            }
            if ("Startup/startup_mik32.S".equals(relativePath)) {
                continue;
            }
            if (relativePath.startsWith("Linker/")) {
                continue;
            }
            if (relativePath.startsWith("framework/")) {
                continue;
            }
            URL fileUrl = FileLocator.toFileURL(entry);
            if (new File(fileUrl.getPath()).isDirectory()) {
                continue;
            }

            IFile target = project.getFile(new Path(relativePath));
            ensureParentFolder(target.getParent(), monitor);
            try (InputStream input = entry.openStream()) {
                if (target.exists()) {
                    target.setContents(input, true, true, monitor);
                } else {
                    target.create(input, true, monitor);
                }
            }
        }
    }

    private void ensureStarterFiles(IProject project, IProgressMonitor monitor) throws CoreException {
        IFile header = project.getFile(new Path("Inc/main.h"));
        if (!header.exists()) {
            ensureParentFolder(header.getParent(), monitor);
            writeFile(header, getDefaultMainHeader(), monitor);
        } else if (projectPage.isCpp()) {
            writeFile(header, getDefaultMainHeader(), monitor);
        }

        IFile cMain = project.getFile(new Path("Src/main.c"));
        IFile cppMain = project.getFile(new Path("Src/main.cpp"));
        if (projectPage.isCpp()) {
            if (cMain.exists() && !cppMain.exists()) {
                cMain.move(cppMain.getFullPath(), true, monitor);
            }
            if (!cppMain.exists()) {
                ensureParentFolder(cppMain.getParent(), monitor);
                writeFile(cppMain, prepareCppMainSource(loadMainSourceTemplate()), monitor);
            } else {
                updateCppMainSource(cppMain, monitor);
            }
        } else if (!cMain.exists()) {
            ensureParentFolder(cMain.getParent(), monitor);
            writeFile(cMain, loadMainSourceTemplate(), monitor);
        }
    }

    private static void writeFile(IFile file, String content, IProgressMonitor monitor) throws CoreException {
        try (InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            if (file.exists()) {
                file.setContents(input, true, true, monitor);
            } else {
                file.create(input, true, monitor);
            }
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    private static void updateCppMainSource(IFile file, IProgressMonitor monitor) throws CoreException {
        writeFile(file, prepareCppMainSource(readFile(file)), monitor);
    }

    private static String prepareCppMainSource(String content) {
        return content
                .replace(" * main.c", " * main.cpp")
                .replace("PCC_InitTypeDef PCC_OscInit = {0};", "PCC_InitTypeDef PCC_OscInit = {};")
                .replace("GPIO_InitTypeDef GPIO_InitStruct = {0};", "GPIO_InitTypeDef GPIO_InitStruct = {};");
    }

    private static String readFile(IFile file) throws CoreException {
        try (InputStream input = file.getContents()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    private static String loadMainSourceTemplate() throws CoreException {
        return loadTemplateFile("Src/main.c");
    }

    private static String loadTemplateFile(String relativePath) throws CoreException {
        Bundle bundle = FrameworkUtil.getBundle(Mik32ProjectWizard.class);
        URL entry = bundle.getEntry("templates/baremetal/files/" + relativePath);
        if (entry == null) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Template file not found: " + relativePath));
        }
        try (InputStream input = entry.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    private static String getDefaultMainHeader() {
        return "#ifndef MAIN_H_" + System.lineSeparator()
                + "#define MAIN_H_" + System.lineSeparator()
                + System.lineSeparator()
                + "#ifdef __cplusplus" + System.lineSeparator()
                + "extern \"C\" {" + System.lineSeparator()
                + "#endif" + System.lineSeparator()
                + System.lineSeparator()
                + "#include \"mik32_hal_pcc.h\"" + System.lineSeparator()
                + "#include \"mik32_hal_gpio.h\"" + System.lineSeparator()
                + System.lineSeparator()
                + "#ifdef __cplusplus" + System.lineSeparator()
                + "}" + System.lineSeparator()
                + "#endif" + System.lineSeparator()
                + System.lineSeparator()
                + "#define LED1_PORT GPIO_0" + System.lineSeparator()
                + "#define LED1_PIN GPIO_PIN_3" + System.lineSeparator()
                + "#define LED2_PORT GPIO_1" + System.lineSeparator()
                + "#define LED2_PIN GPIO_PIN_3" + System.lineSeparator()
                + System.lineSeparator()
                + "#endif" + System.lineSeparator();
    }

    private void createMetadataFile(IProject project, IProgressMonitor monitor) throws CoreException {
        String content = "language=" + (projectPage.isCpp() ? "c++" : "c") + System.lineSeparator()
                + "binaryType=executable" + System.lineSeparator() + "buildSystem=" + mode.buildSystem
                + System.lineSeparator() + "frameworkMode=" + (projectPage.isLinkFramework() ? "link" : "copy")
                + System.lineSeparator() + "frameworkPath=" + Mik32PluginPreferences.getFrameworkPath()
                + System.lineSeparator();
        IFile file = project.getFile(".mik32project");
        try (InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            if (file.exists()) {
                file.setContents(input, true, true, monitor);
            } else {
                file.create(input, true, monitor);
            }
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    static void createLaunchConfigurations(IProject project, IProgressMonitor monitor) throws CoreException {
        String projectName = project.getName();
        String launchPrefix = getLaunchProgrammerPrefix();
        String uploadName = launchPrefix + " Upload";
        String debugName = launchPrefix + " Debug";
        String uploadDebugName = launchPrefix + " Upload Debug";

        deleteGeneratedLaunchConfigurations(project, projectName, monitor);
        writeFile(project.getFile(new Path(uploadName + ".launch")), createUploadLaunch(projectName), monitor);
        writeFile(project.getFile(new Path(debugName + ".launch")), createDebugLaunch(projectName), monitor);
        writeFile(project.getFile(new Path(uploadDebugName + ".launch")),
                createUploadDebugLaunch(uploadName, debugName), monitor);
    }

    static void updateLaunchConfigurationsForOpenProjects(IProgressMonitor monitor) throws CoreException {
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (project.isOpen() && project.getFile(".mik32project").exists()) {
                createLaunchConfigurations(project, monitor);
            }
        }
    }

    private static void deleteGeneratedLaunchConfigurations(IProject project, String projectName, IProgressMonitor monitor)
            throws CoreException {
        for (IResource member : project.members()) {
            if (member instanceof IFile && member.getName().endsWith(".launch")
                    && isGeneratedLaunchFile((IFile) member, projectName)) {
                member.delete(true, monitor);
            }
        }
    }

    private static boolean isGeneratedLaunchFile(IFile file, String projectName) throws CoreException {
        String name = file.getName();
        if (name.equals(projectName + " Upload.launch")
                || name.equals(projectName + " Debug.launch")
                || name.equals(projectName + " Upload Debug.launch")
                || name.endsWith(" " + projectName + " Upload.launch")
                || name.endsWith(" " + projectName + " Debug.launch")
                || name.endsWith(" " + projectName + " Upload Debug.launch")) {
            return true;
        }
        try (InputStream input = file.getContents()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return content.contains("key=\"" + GENERATED_LAUNCH_ATTRIBUTE + "\"");
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    private static String createUploadLaunch(String projectName) {
        String uploaderExecutable = getUploaderExecutable();
        String openocdExecutable = getOpenocdExecutable();
        String interfaceConfig = getInterfaceConfigPath();
        String targetConfig = getTargetConfigPath();
        String arguments = "${project_name}.hex --run-openocd --openocd-exec=\"" + openocdExecutable
                + "\" --openocd-interface=\"" + interfaceConfig + "\" --openocd-target=\"" + targetConfig + "\"";

        return xmlHeader()
                + "<launchConfiguration type=\"org.eclipse.ui.externaltools.ProgramLaunchConfigurationType\">"
                + nl()
                + bool("org.eclipse.debug.core.ATTR_FORCE_SYSTEM_CONSOLE_ENCODING", false)
                + bool(GENERATED_LAUNCH_ATTRIBUTE, true)
                + "    <listAttribute key=\"org.eclipse.debug.ui.favoriteGroups\">" + nl()
                + "        <listEntry value=\"org.eclipse.ui.externaltools.launchGroup\"/>" + nl()
                + "    </listAttribute>" + nl()
                + str("org.eclipse.ui.externaltools.ATTR_LAUNCH_CONFIGURATION_BUILD_SCOPE", "${project}")
                + str("org.eclipse.ui.externaltools.ATTR_LOCATION", uploaderExecutable)
                + str("org.eclipse.ui.externaltools.ATTR_TOOL_ARGUMENTS", arguments)
                + str("org.eclipse.ui.externaltools.ATTR_WORKING_DIRECTORY",
                        "${project_loc}/${config_name:${project_name}}")
                + "</launchConfiguration>" + nl();
    }

    private static String createUploadDebugLaunch(String uploadName, String debugName) {
        return xmlHeader()
                + "<launchConfiguration type=\"org.eclipse.debug.core.groups.GroupLaunchConfigurationType\">"
                + nl()
                + bool(GENERATED_LAUNCH_ATTRIBUTE, true)
                + str("org.eclipse.debug.core.launchGroup.0.action", "WAIT_FOR_TERMINATION")
                + bool("org.eclipse.debug.core.launchGroup.0.adoptIfRunning", false)
                + bool("org.eclipse.debug.core.launchGroup.0.enabled", true)
                + str("org.eclipse.debug.core.launchGroup.0.mode", "run")
                + str("org.eclipse.debug.core.launchGroup.0.name", uploadName)
                + str("org.eclipse.debug.core.launchGroup.1.action", "NONE")
                + bool("org.eclipse.debug.core.launchGroup.1.adoptIfRunning", false)
                + bool("org.eclipse.debug.core.launchGroup.1.enabled", true)
                + str("org.eclipse.debug.core.launchGroup.1.mode", "debug")
                + str("org.eclipse.debug.core.launchGroup.1.name", debugName)
                + "    <listAttribute key=\"org.eclipse.debug.ui.favoriteGroups\">" + nl()
                + "        <listEntry value=\"org.eclipse.debug.ui.launchGroup.debug\"/>" + nl()
                + "        <listEntry value=\"org.eclipse.debug.ui.launchGroup.run\"/>" + nl()
                + "    </listAttribute>" + nl()
                + "</launchConfiguration>" + nl();
    }

    private static String createDebugLaunch(String projectName) {
        String openocdExecutable = getOpenocdExecutable();
        String interfaceConfig = getInterfaceConfigPath();
        String targetConfig = getTargetConfigPath();
        String serverOther = "-f \"" + interfaceConfig + "\" -f \"" + targetConfig
                + "\" -c \"init\" -c \"reset halt\"";
        String gdbCommands = "set mem inaccessible-by-default off&#13;&#10;"
                + "mem 0x01000000 0x01002000 ro&#13;&#10;"
                + "mem 0x80000000 0xffffffff ro&#13;&#10;"
                + "set arch riscv:rv32&#13;&#10;"
                + "set remotetimeout 10&#13;&#10;"
                + "set remote hardware-breakpoint-limit 2";

        return xmlHeader()
                + "<launchConfiguration type=\"ilg.gnumcueclipse.debug.gdbjtag.openocd.launchConfigurationType\">"
                + nl()
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doContinue", false)
                + bool(GENERATED_LAUNCH_ATTRIBUTE, true)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doDebugInRam", false)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doFirstReset", true)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doGdbServerAllocateConsole", true)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doGdbServerAllocateTelnetConsole", false)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doSecondReset", false)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doStartGdbCLient", true)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.doStartGdbServer", true)
                + bool("ilg.gnumcueclipse.debug.gdbjtag.openocd.enableSemihosting", true)
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.firstResetType", "init")
                + strRaw("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbClientOtherCommands", gdbCommands)
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbClientOtherOptions", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerConnectionAddress", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerExecutable", openocdExecutable)
                + intAttr("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerGdbPortNumber", 3333)
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerLog", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerOther", serverOther)
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerTclPortNumber", "6666")
                + intAttr("ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServerTelnetPortNumber", 4444)
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.otherInitCommands", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.otherRunCommands", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.openocd.secondResetType", "")
                + str("ilg.gnumcueclipse.debug.gdbjtag.svdPath", "${workspace_loc}\\mik32v2.svd")
                + str("org.eclipse.cdt.debug.gdbjtag.core.imageFileName", "")
                + str("org.eclipse.cdt.debug.gdbjtag.core.imageOffset", "")
                + str("org.eclipse.cdt.debug.gdbjtag.core.ipAddress", "localhost")
                + str("org.eclipse.cdt.debug.gdbjtag.core.jtagDevice", "GNU MCU OpenOCD")
                + bool("org.eclipse.cdt.debug.gdbjtag.core.loadImage", false)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.loadSymbols", true)
                + str("org.eclipse.cdt.debug.gdbjtag.core.pcRegister", "")
                + intAttr("org.eclipse.cdt.debug.gdbjtag.core.portNumber", 3333)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.setPcRegister", false)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.setResume", false)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.setStopAt", false)
                + str("org.eclipse.cdt.debug.gdbjtag.core.stopAt", "")
                + str("org.eclipse.cdt.debug.gdbjtag.core.symbolsFileName", "")
                + str("org.eclipse.cdt.debug.gdbjtag.core.symbolsOffset", "")
                + bool("org.eclipse.cdt.debug.gdbjtag.core.useFileForImage", false)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.useFileForSymbols", false)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.useProjBinaryForImage", true)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.useProjBinaryForSymbols", true)
                + bool("org.eclipse.cdt.debug.gdbjtag.core.useRemoteTarget", true)
                + str("org.eclipse.cdt.dsf.gdb.DEBUG_NAME", getGdbExecutable())
                + bool("org.eclipse.cdt.dsf.gdb.UPDATE_THREADLIST_ON_SUSPEND", false)
                + intAttr("org.eclipse.cdt.launch.ATTR_BUILD_BEFORE_LAUNCH_ATTR", 2)
                + str("org.eclipse.cdt.launch.COREFILE_PATH", "")
                + str("org.eclipse.cdt.launch.PROGRAM_NAME",
                        "${project_loc}/${config_name:${project_name}}/${project_name}.elf")
                + str("org.eclipse.cdt.launch.PROJECT_ATTR", projectName)
                + bool("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_AUTO_ATTR", false)
                + str("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", "")
                + bool("org.eclipse.debug.core.ATTR_FORCE_SYSTEM_CONSOLE_ENCODING", false)
                + "    <listAttribute key=\"org.eclipse.debug.ui.favoriteGroups\">" + nl()
                + "        <listEntry value=\"org.eclipse.debug.ui.launchGroup.debug\"/>" + nl()
                + "        <listEntry value=\"org.eclipse.debug.ui.launchGroup.run\"/>" + nl()
                + "    </listAttribute>" + nl()
                + str("org.eclipse.dsf.launch.MEMORY_BLOCKS",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>&#13;&#10;<memoryBlockExpressionList context=\"Context string\"/>&#13;&#10;")
                + str("process_factory_id", "org.eclipse.cdt.dsf.gdb.GdbProcessFactory")
                + "</launchConfiguration>" + nl();
    }

    private static String getUploaderExecutable() {
        String uploaderPath = normalizePath(Mik32PluginPreferences.getUploaderPath());
        if (!uploaderPath.isEmpty()) {
            return uploaderPath + "/mik32_upload.exe";
        }
        return "${eclipse_home}/../uploader/mik32_upload.exe";
    }

    private static String getOpenocdExecutable() {
        String uploaderPath = normalizePath(Mik32PluginPreferences.getUploaderPath());
        if (!uploaderPath.isEmpty()) {
            File openocd = new File(new File(uploaderPath).getParentFile(), "openocd/bin/openocd.exe");
            if (openocd.isFile()) {
                return normalizePath(openocd.getAbsolutePath());
            }
        }
        return "${eclipse_home}/../openocd/bin/openocd.exe";
    }

    private static String getGdbExecutable() {
        return "${eclipse_home}/../riscv-gcc/bin/riscv-none-elf-gdb";
    }

    private static String getInterfaceConfigPath() {
        String programmerConfig = Mik32PluginPreferences.getProgrammerConfig();
        if (programmerConfig == null || programmerConfig.trim().isEmpty()) {
            programmerConfig = "kotelink.cfg";
        }
        return getOpenocdScriptsPath() + "/interface/" + programmerConfig.trim();
    }

    private static String getLaunchProgrammerPrefix() {
        String programmerConfig = Mik32PluginPreferences.getProgrammerConfig();
        if (programmerConfig == null || programmerConfig.trim().isEmpty()) {
            return "KoteLink";
        }
        String name = programmerConfig.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".cfg")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.isEmpty()) {
            return "KoteLink";
        }
        return name;
    }

    private static String getTargetConfigPath() {
        return getOpenocdScriptsPath() + "/target/mik32.cfg";
    }

    private static String getOpenocdScriptsPath() {
        String uploaderPath = normalizePath(Mik32PluginPreferences.getUploaderPath());
        if (!uploaderPath.isEmpty()) {
            File root = new File(uploaderPath);
            File scripts = new File(root, "openocd-scripts");
            if (scripts.isDirectory()) {
                return normalizePath(scripts.getAbsolutePath());
            }
            scripts = new File(root, "uploader/openocd-scripts");
            if (scripts.isDirectory()) {
                return normalizePath(scripts.getAbsolutePath());
            }
        }
        return "${eclipse_home}/../uploader/openocd-scripts";
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replace('\\', '/');
    }

    private static String xmlHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + nl();
    }

    private static String bool(String key, boolean value) {
        return "    <booleanAttribute key=\"" + xml(key) + "\" value=\"" + value + "\"/>" + nl();
    }

    private static String intAttr(String key, int value) {
        return "    <intAttribute key=\"" + xml(key) + "\" value=\"" + value + "\"/>" + nl();
    }

    private static String str(String key, String value) {
        return strRaw(key, xml(value));
    }

    private static String strRaw(String key, String escapedValue) {
        return "    <stringAttribute key=\"" + xml(key) + "\" value=\"" + escapedValue + "\"/>" + nl();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String nl() {
        return System.lineSeparator();
    }

    private void createCMakeLists(IProject project, IProgressMonitor monitor) throws CoreException {
        if (mode != ProjectMode.CMAKE) {
            return;
        }
        IFile file = project.getFile("CMakeLists.txt");
        if (file.exists()) {
            return;
        }
        String language = projectPage.isCpp() ? "CXX" : "C";
        String sourceFile = projectPage.isCpp() ? "Src/main.cpp" : "Src/main.c";
        String content = "cmake_minimum_required(VERSION 3.20)" + System.lineSeparator()
                + "project(" + project.getName() + " " + language + ")" + System.lineSeparator()
                + "add_executable(${PROJECT_NAME} " + sourceFile + ")" + System.lineSeparator();
        try (InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            file.create(input, true, monitor);
        } catch (java.io.IOException exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
        }
    }

    static void ensureParentFolder(IContainer container, IProgressMonitor monitor) throws CoreException {
        if (container == null || container.exists()) {
            return;
        }
        ensureParentFolder(container.getParent(), monitor);
        if (container instanceof IFolder && !container.exists()) {
            ((IFolder) container).create(true, true, monitor);
        }
    }

    private void log(Throwable exception) {
        Platform.getLog(FrameworkUtil.getBundle(Mik32ProjectWizard.class))
                .log(new Status(IStatus.ERROR, Startup.PLUGIN_ID, exception.getMessage(), exception));
    }

    private static Throwable unwrap(Exception exception) {
        if (exception instanceof InvocationTargetException target && target.getCause() != null) {
            return target.getCause();
        }
        return exception;
    }

    private static void setOptionValue(IConfiguration config, IToolChain tc, String optionSuperClassId, Object value)
            throws CoreException {
        IOption opt = tc.getOptionBySuperClassId(optionSuperClassId);
        if (opt == null) {
            for (ITool tool : tc.getTools()) {
                opt = tool.getOptionBySuperClassId(optionSuperClassId);
                if (opt != null) {
                    applyToolOption(config, tool, opt, optionSuperClassId, value);
                    return;
                }
            }
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "CDT option is not available: " + optionSuperClassId));
        }

        applyToolChainOption(config, tc, opt, optionSuperClassId, value);
    }

    private static void useInternalBuilder(IConfiguration config) throws Exception {
        IBuilder currentBuilder = config.getBuilder();
        if (currentBuilder != null && currentBuilder.isInternalBuilder()) {
            return;
        }

        IBuilder internalBuilder = ManagedBuildManager.getInternalBuilder();
        if (internalBuilder != null && config.supportsBuild(true) && config.isBuilderCompatible(internalBuilder)) {
            String builderId = ManagedBuildManager.calculateChildId(internalBuilder.getId(), null);
            config.changeBuilder(internalBuilder, builderId, internalBuilder.getName());
            config.setManagedBuildOn(true);
        }
    }

    private static void applyToolOption(IConfiguration config, ITool tool, IOption opt, String optionSuperClassId,
            Object value) throws CoreException {
        applyOptionValue(optionSuperClassId, value, new OptionValueWriter() {
            @Override
            public void write(boolean selected) throws CoreException {
                ManagedBuildManager.setOption(config, tool, opt, selected);
            }

            @Override
            public void write(String text) throws CoreException {
                ManagedBuildManager.setOption(config, tool, opt, text);
            }

            @Override
            public void write(String[] values) throws CoreException {
                ManagedBuildManager.setOption(config, tool, opt, values);
            }
        });
    }

    private static void applyToolChainOption(IConfiguration config, IToolChain tc, IOption opt, String optionSuperClassId,
            Object value) throws CoreException {
        applyOptionValue(optionSuperClassId, value, new OptionValueWriter() {
            @Override
            public void write(boolean selected) throws CoreException {
                ManagedBuildManager.setOption(config, tc, opt, selected);
            }

            @Override
            public void write(String text) throws CoreException {
                ManagedBuildManager.setOption(config, tc, opt, text);
            }

            @Override
            public void write(String[] values) throws CoreException {
                ManagedBuildManager.setOption(config, tc, opt, values);
            }
        });
    }

    private static void applyOptionValue(String optionSuperClassId, Object value, OptionValueWriter writer)
            throws CoreException {
        try {
            if (value instanceof Boolean selected) {
                writer.write(selected.booleanValue());
            } else if (value instanceof String text) {
                writer.write(text);
            } else if (value instanceof String[] values) {
                writer.write(values);
            } else if (value == null) {
                throw new IllegalArgumentException("Unsupported null option value");
            } else {
                throw new IllegalArgumentException("Unsupported option value type: " + value.getClass().getName());
            }
        } catch (Exception exception) {
            throw new CoreException(new Status(IStatus.ERROR, Startup.PLUGIN_ID,
                    "Failed to set CDT option: " + optionSuperClassId, exception));
        }
    }

    private interface OptionValueWriter {
        void write(boolean selected) throws CoreException;

        void write(String text) throws CoreException;

        void write(String[] values) throws CoreException;
    }
}
