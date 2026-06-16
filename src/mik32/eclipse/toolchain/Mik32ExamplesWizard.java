package mik32.eclipse.toolchain;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.osgi.framework.FrameworkUtil;

public final class Mik32ExamplesWizard extends Wizard {
    private Mik32ExamplesPage examplesPage;

    public Mik32ExamplesWizard() {
        setWindowTitle("MIK32 Examples");
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {
        examplesPage = new Mik32ExamplesPage();
        addPage(examplesPage);
    }

    @Override
    public boolean performFinish() {
        try {
            getContainer().run(false, true, monitor -> {
                try {
                    cloneExample(monitor);
                } catch (Exception exception) {
                    throw new InvocationTargetException(exception);
                }
            });
            return true;
        } catch (Exception exception) {
            Throwable cause = unwrap(exception);
            MessageDialog.openError(getShell(), "MIK32 Examples",
                    "Failed to clone MIK32 example: " + cause.getMessage());
            Platform.getLog(FrameworkUtil.getBundle(Mik32ExamplesWizard.class))
                    .log(new Status(IStatus.ERROR, Startup.PLUGIN_ID, cause.getMessage(), cause));
            return false;
        }
    }

    private void cloneExample(IProgressMonitor monitor) throws Exception {
        Mik32ExamplesPage.ExampleDescriptor example = examplesPage.getSelectedExample();
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProject project = workspace.getRoot().getProject(examplesPage.getProjectName());
        IProjectDescription description = workspace.newProjectDescription(project.getName());
        if (!examplesPage.useDefaultLocation()) {
            description.setLocationURI(new File(examplesPage.getLocation(), project.getName()).toURI());
        }

        project.create(description, monitor);
        project.open(monitor);
        copyDirectory(example.getPath(), project, monitor);
        Mik32ProjectWizard.ensureFrameworkLinked(project, monitor);
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        Mik32ProjectWizard.configureManagedProject(project, monitor, true);
        createMetadata(project, example, monitor);
        Mik32ProjectWizard.createLaunchConfigurations(project, monitor);
    }

    private void copyDirectory(Path sourceRoot, IProject project, IProgressMonitor monitor) throws Exception {
        try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
            java.util.Iterator<Path> iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path source = iterator.next();
                Path relative = sourceRoot.relativize(source);
                if (shouldSkip(relative)) {
                    continue;
                }
                IFile target = project.getFile(new org.eclipse.core.runtime.Path(relative.toString().replace('\\', '/')));
                Mik32ProjectWizard.ensureParentFolder(target.getParent(), monitor);
                try (InputStream input = Files.newInputStream(source)) {
                    if (target.exists()) {
                        target.setContents(input, true, true, monitor);
                    } else {
                        target.create(input, true, monitor);
                    }
                }
            }
        }
    }

    private boolean shouldSkip(Path relative) {
        String normalized = relative.toString().replace('\\', '/');
        return normalized.equals(".project")
                || normalized.equals(".cproject")
                || normalized.equals("Startup/startup_mik32.S")
                || normalized.startsWith("framework/")
                || normalized.startsWith(".git/")
                || normalized.startsWith(".pio/")
                || normalized.startsWith("Debug/")
                || normalized.startsWith("Release/");
    }

    private void createMetadata(IProject project, Mik32ExamplesPage.ExampleDescriptor example, IProgressMonitor monitor)
            throws CoreException {
        String content = "source=example" + System.lineSeparator()
                + "exampleName=" + example.getName() + System.lineSeparator()
                + "examplePath=" + example.getPath() + System.lineSeparator()
                + "buildSystem=managed" + System.lineSeparator()
                + "frameworkMode=link" + System.lineSeparator()
                + "frameworkPath=" + Mik32PluginPreferences.getFrameworkPath() + System.lineSeparator();
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

    private static Throwable unwrap(Exception exception) {
        if (exception instanceof InvocationTargetException target && target.getCause() != null) {
            return target.getCause();
        }
        return exception;
    }
}
