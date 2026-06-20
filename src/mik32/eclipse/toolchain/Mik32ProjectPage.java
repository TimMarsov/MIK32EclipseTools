package mik32.eclipse.toolchain;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

final class Mik32ProjectPage extends WizardPage {
    private final boolean showMemorySelection;
    private Text projectNameText;
    private Button useDefaultLocationButton;
    private Text locationText;
    private Button browseButton;
    private Button cLanguageButton;
    private Button cppLanguageButton;
    private Button copyFrameworkButton;
    private Button linkFrameworkButton;
    private Combo memoryCombo;

    Mik32ProjectPage(String title, boolean showMemorySelection) {
        super("mik32ProjectPage");
        this.showMemorySelection = showMemorySelection;
        setTitle(title);
        setDescription("Create a MIK32 embedded project.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        createProjectGroup(root);
        createLanguageGroup(root);
        createFrameworkGroup(root);
        if (showMemorySelection) {
            createMemoryGroup(root);
        }

        setControl(root);
        Dialog.applyDialogFont(root);
        validate();
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

    boolean isCpp() {
        return cppLanguageButton.getSelection();
    }

    boolean isLinkFramework() {
        return linkFrameworkButton.getSelection();
    }

    String getMemoryType() {
        if (memoryCombo == null) {
            return "eeprom";
        }
        return switch (memoryCombo.getSelectionIndex()) {
        case 1 -> "flash";
        case 2 -> "ram";
        default -> "eeprom";
        };
    }

    private void createProjectGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Project");
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
        locationText.setText(getWorkspaceLocation());
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

    private void createFrameworkGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Framework Options");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        copyFrameworkButton = new Button(group, SWT.RADIO);
        copyFrameworkButton.setText("Copy framework to project");
        copyFrameworkButton.setSelection(true);

        linkFrameworkButton = new Button(group, SWT.RADIO);
        linkFrameworkButton.setText("Link to framework (referenced)");

        copyFrameworkButton.addListener(SWT.Selection, event -> validate());
        linkFrameworkButton.addListener(SWT.Selection, event -> validate());
    }

    private void createLanguageGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Language");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        cLanguageButton = new Button(group, SWT.RADIO);
        cLanguageButton.setText("C");
        cLanguageButton.setSelection(true);

        cppLanguageButton = new Button(group, SWT.RADIO);
        cppLanguageButton.setText("C++");
    }

    private void createMemoryGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Program Memory");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label label = new Label(group, SWT.NONE);
        label.setText("Link application to:");

        memoryCombo = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
        memoryCombo.setItems("EEPROM", "Flash (SPIFI)", "RAM");
        memoryCombo.select(0);
        memoryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void browseLocation() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText("Select MIK32 project location");
        dialog.setFilterPath(locationText.getText());
        String selected = dialog.open();
        if (selected != null) {
            locationText.setText(selected);
        }
    }

    private void validate() {
        String projectName = getProjectName();
        if (projectName.isEmpty()) {
            setErrorMessage("Empty project name is not supported");
            setPageComplete(false);
            return;
        }
        if (ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).exists()) {
            setErrorMessage("Project already exists");
            setPageComplete(false);
            return;
        }
        if (!useDefaultLocation() && getLocation().isEmpty()) {
            setErrorMessage("Project location is not specified");
            setPageComplete(false);
            return;
        }
        if (!useDefaultLocation() && new File(getLocation(), projectName).exists()) {
            setErrorMessage("Target project folder already exists");
            setPageComplete(false);
            return;
        }
        if (isLinkFramework()) {
            String fwPath = Mik32PluginPreferences.getFrameworkPath();
            if (fwPath == null || fwPath.trim().isEmpty()) {
                setErrorMessage("Framework path is not configured in Preferences (Window -> Preferences -> MIK32)");
                setPageComplete(false);
                return;
            }
            File fwDir = new File(fwPath);
            if (!fwDir.exists() || !fwDir.isDirectory()) {
                setErrorMessage("Framework path configured in Preferences does not exist or is not a directory");
                setPageComplete(false);
                return;
            }
        }
        setErrorMessage(null);
        setPageComplete(true);
    }

    private String getWorkspaceLocation() {
        return ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
    }
}
