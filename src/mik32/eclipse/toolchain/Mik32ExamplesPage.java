package mik32.eclipse.toolchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

final class Mik32ExamplesPage extends WizardPage {
    static final class ExampleDescriptor {
        private final String name;
        private final Path path;

        ExampleDescriptor(String name, Path path) {
            this.name = name;
            this.path = path;
        }

        String getName() {
            return name;
        }

        Path getPath() {
            return path;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final java.util.List<ExampleDescriptor> examples = new ArrayList<>();
    private org.eclipse.swt.widgets.List examplesList;
    private Text projectNameText;
    private Button useDefaultLocationButton;
    private Text locationText;
    private Button browseButton;

    Mik32ExamplesPage() {
        super("mik32ExamplesPage");
        setTitle("MIK32 Examples");
        setDescription("Clone a MIK32 example into the workspace.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        createExamplesGroup(root);
        createProjectGroup(root);

        setControl(root);
        Dialog.applyDialogFont(root);
        validate();
    }

    ExampleDescriptor getSelectedExample() {
        int index = examplesList.getSelectionIndex();
        return index >= 0 ? examples.get(index) : null;
    }

    String getProjectName() {
        return projectNameText.getText().trim();
    }

    boolean useDefaultLocation() {
        return useDefaultLocationButton.getSelection();
    }

    String getLocation() {
        return locationText.getText().trim();
    }

    private void createExamplesGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Examples");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        examplesList = new org.eclipse.swt.widgets.List(group, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 220;
        examplesList.setLayoutData(data);

        loadExamples();
        for (ExampleDescriptor example : examples) {
            examplesList.add(example.getName());
        }
        examplesList.addListener(SWT.Selection, event -> {
            ExampleDescriptor selected = getSelectedExample();
            if (selected != null && projectNameText.getText().trim().isEmpty()) {
                projectNameText.setText(selected.getName());
            }
            validate();
        });
    }

    private void createProjectGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Target Project");
        group.setLayout(new GridLayout(3, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label nameLabel = new Label(group, SWT.NONE);
        nameLabel.setText("Project Name:");

        projectNameText = new Text(group, SWT.BORDER);
        projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        useDefaultLocationButton = new Button(group, SWT.CHECK);
        useDefaultLocationButton.setText("Use default location");
        useDefaultLocationButton.setSelection(true);
        useDefaultLocationButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

        Label locationLabel = new Label(group, SWT.NONE);
        locationLabel.setText("Location:");

        locationText = new Text(group, SWT.BORDER);
        locationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        locationText.setText(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
        locationText.setEnabled(false);

        browseButton = new Button(group, SWT.PUSH);
        browseButton.setText("Browse...");
        browseButton.setEnabled(false);

        ModifyListener validateListener = event -> validate();
        projectNameText.addModifyListener(validateListener);
        locationText.addModifyListener(validateListener);
        useDefaultLocationButton.addListener(SWT.Selection, event -> {
            boolean customLocation = !useDefaultLocationButton.getSelection();
            locationText.setEnabled(customLocation);
            browseButton.setEnabled(customLocation);
            validate();
        });
        browseButton.addListener(SWT.Selection, event -> browseLocation());
    }

    private void loadExamples() {
        String examplesPath = Mik32PluginPreferences.getExamplesPath();
        if (examplesPath.isBlank()) {
            return;
        }
        Path root = Path.of(examplesPath);
        if (!Files.isDirectory(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(this::looksLikeExample)
                    .map(path -> new ExampleDescriptor(path.getFileName().toString(), path))
                    .sorted(Comparator.comparing(ExampleDescriptor::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(examples::add);
        } catch (java.io.IOException exception) {
            setErrorMessage(exception.getMessage());
        }
    }

    private boolean looksLikeExample(Path path) {
        return Files.exists(path.resolve("src").resolve("main.c"))
                || Files.exists(path.resolve("platformio.ini"))
                || Files.exists(path.resolve(".project"));
    }

    private void browseLocation() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText("Select target location");
        dialog.setFilterPath(locationText.getText());
        String selected = dialog.open();
        if (selected != null) {
            locationText.setText(selected);
        }
    }

    private void validate() {
        String examplesPath = Mik32PluginPreferences.getExamplesPath();
        if (examplesPath.isBlank()) {
            setErrorMessage("Examples path is not configured. Set it in Window > Preferences > MIK32.");
            setPageComplete(false);
            return;
        }
        if (!Files.isDirectory(Path.of(examplesPath))) {
            setErrorMessage("Examples path does not exist: " + examplesPath);
            setPageComplete(false);
            return;
        }
        String frameworkPath = Mik32PluginPreferences.getFrameworkPath();
        if (frameworkPath.isBlank()) {
            setErrorMessage("Framework path is not configured. Set it in Window > Preferences > MIK32.");
            setPageComplete(false);
            return;
        }
        if (!Files.isDirectory(Path.of(frameworkPath))) {
            setErrorMessage("Framework path does not exist: " + frameworkPath);
            setPageComplete(false);
            return;
        }
        if (examples.isEmpty()) {
            setErrorMessage("No examples found in: " + examplesPath);
            setPageComplete(false);
            return;
        }
        if (getSelectedExample() == null) {
            setErrorMessage("Select an example to clone");
            setPageComplete(false);
            return;
        }
        if (getProjectName().isEmpty()) {
            setErrorMessage("Empty project name is not supported");
            setPageComplete(false);
            return;
        }
        if (ResourcesPlugin.getWorkspace().getRoot().getProject(getProjectName()).exists()) {
            setErrorMessage("Project already exists");
            setPageComplete(false);
            return;
        }
        if (!useDefaultLocation() && getLocation().isEmpty()) {
            setErrorMessage("Project location is not specified");
            setPageComplete(false);
            return;
        }
        if (!useDefaultLocation() && new File(getLocation(), getProjectName()).exists()) {
            setErrorMessage("Target project folder already exists");
            setPageComplete(false);
            return;
        }
        setErrorMessage(null);
        setPageComplete(true);
    }
}
