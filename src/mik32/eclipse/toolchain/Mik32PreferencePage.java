package mik32.eclipse.toolchain;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public final class Mik32PreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    private Text frameworkPathText;
    private Text examplesPathText;
    private Text uploaderPathText;
    private Combo programmerCombo;
    private String[] programmerValues = new String[0];

    public Mik32PreferencePage() {
        setPreferenceStore(new ScopedPreferenceStore(org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE,
                Startup.PLUGIN_ID));
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(3, false));
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label headerLabel = new Label(root, SWT.NONE);
        headerLabel.setText("MrMarsov MIK32 Projects Tools");
        org.eclipse.swt.graphics.FontData[] fontData = headerLabel.getFont().getFontData();
        for (int i = 0; i < fontData.length; i++) {
            fontData[i].setStyle(SWT.BOLD);
        }
        org.eclipse.swt.graphics.Font boldFont = new org.eclipse.swt.graphics.Font(parent.getDisplay(), fontData);
        headerLabel.setFont(boldFont);
        headerLabel.addDisposeListener(event -> boldFont.dispose());

        GridData headerGrid = new GridData(SWT.FILL, SWT.CENTER, true, false);
        headerGrid.horizontalSpan = 3;
        headerLabel.setLayoutData(headerGrid);

        Label descLabel = new Label(root, SWT.NONE);
        descLabel.setText("Configure MIK32 SDK locations used by project wizards.");
        GridData descGrid = new GridData(SWT.FILL, SWT.CENTER, true, false);
        descGrid.horizontalSpan = 3;
        descGrid.verticalIndent = 2;
        descGrid.verticalSpan = 10;
        descLabel.setLayoutData(descGrid);

        frameworkPathText = createDirectoryRow(root, "Framework path:", Mik32PluginPreferences.FRAMEWORK_PATH);
        examplesPathText = createDirectoryRow(root, "Examples path:", Mik32PluginPreferences.EXAMPLES_PATH);
        uploaderPathText = createDirectoryRow(root, "UPLOADER_MIK32 path:", Mik32PluginPreferences.UPLOADER_PATH);
        uploaderPathText.addModifyListener(event -> refreshProgrammers());

        Label programmerLabel = new Label(root, SWT.NONE);
        programmerLabel.setText("Programmer:");

        programmerCombo = new Combo(root, SWT.DROP_DOWN | SWT.READ_ONLY);
        programmerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button refreshButton = new Button(root, SWT.PUSH);
        refreshButton.setText("Refresh");
        refreshButton.addListener(SWT.Selection, event -> refreshProgrammers());

        Group group = new Group(root, SWT.NONE);
        group.setText("About & Support (О плагине и поддержке)");
        group.setLayout(new GridLayout(1, false));
        GridData groupGridData = new GridData(SWT.FILL, SWT.FILL, true, false);
        groupGridData.horizontalSpan = 3;
        groupGridData.verticalIndent = 15;
        group.setLayoutData(groupGridData);

        Link link = new Link(group, SWT.WRAP);
        link.setText("Здравствуй, дорогой пользователь! Это расширение было создано, чтобы облегчить процесс разработки для МИК32.\n\n"
            + "В настройках IDE (Окно -> Параметры -> MIK32 или Window -> Preferences -> MIK32) Вы также можете ознакомиться с необходимыми параметрами и выбором программатора.\n\n"
            + "Компания, в которой мы разрабатывали отечественное оборудование и устройства, сейчас на грани из-за давления и отсутствия поддержки со стороны государства. Я сейчас нахожусь в сложном положении и закрываю крупную задолженность, из-за кризиса я остался без работы и на грани депрессии, буду очень рад и благодарен поддержке, даже моральной.\n\n"
            + "Связаться со мной можно по почте: <a href=\"mailto:tim.marsov@gmail.com\">tim.marsov@gmail.com</a>\n"
            + "Поддержать финансово:\n"
            + "  • Boosty: <a href=\"https://boosty.to/mrmarsov\">boosty.to/mrmarsov</a>\n"
            + "  • YooMoney: <a href=\"https://yoomoney.ru/fundraise/1IDQG8PSQV7.260615\">yoomoney.ru/fundraise/1IDQG8PSQV7.260615</a>\n\n"
            + "GitHub репозиторий:\n"
            + "  • <a href=\"https://github.com/TimMarsov/MIK32EclipseTools\">github.com/TimMarsov/MIK32EclipseTools</a>");
        
        GridData linkGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        linkGridData.widthHint = 400;
        link.setLayoutData(linkGridData);
        link.addListener(SWT.Selection, event -> org.eclipse.swt.program.Program.launch(event.text));

        refreshProgrammers();
        return root;
    }

    @Override
    public boolean performOk() {
        IPreferenceStore store = getPreferenceStore();
        String oldUploaderPath = store.getString(Mik32PluginPreferences.UPLOADER_PATH);
        String oldProgrammerConfig = store.getString(Mik32PluginPreferences.PROGRAMMER_CONFIG);
        String newUploaderPath = uploaderPathText.getText().trim();
        String newProgrammerConfig = getSelectedProgrammerValue();

        store.setValue(Mik32PluginPreferences.FRAMEWORK_PATH, frameworkPathText.getText().trim());
        store.setValue(Mik32PluginPreferences.EXAMPLES_PATH, examplesPathText.getText().trim());
        store.setValue(Mik32PluginPreferences.UPLOADER_PATH, newUploaderPath);
        store.setValue(Mik32PluginPreferences.PROGRAMMER_CONFIG, newProgrammerConfig);
        if (!oldUploaderPath.equals(newUploaderPath) || !oldProgrammerConfig.equals(newProgrammerConfig)) {
            try {
                Mik32ProjectWizard.updateLaunchConfigurationsForOpenProjects(new NullProgressMonitor());
            } catch (CoreException exception) {
                MessageDialog.openError(getShell(), "MIK32",
                        "Failed to update MIK32 launch configurations: " + exception.getMessage());
                return false;
            }
        }
        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        frameworkPathText.setText("");
        examplesPathText.setText("");
        uploaderPathText.setText("");
        refreshProgrammers();
        super.performDefaults();
    }

    private Text createDirectoryRow(Composite parent, String label, String preferenceKey) {
        Label pathLabel = new Label(parent, SWT.NONE);
        pathLabel.setText(label);

        Text text = new Text(parent, SWT.BORDER);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        text.setText(getPreferenceStore().getString(preferenceKey));

        Button browseButton = new Button(parent, SWT.PUSH);
        browseButton.setText("Browse...");
        browseButton.addListener(SWT.Selection, event -> browseDirectory(text));
        return text;
    }

    private void browseDirectory(Text target) {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setFilterPath(target.getText().trim());
        String selected = dialog.open();
        if (selected != null) {
            target.setText(selected);
        }
    }

    private void refreshProgrammers() {
        if (programmerCombo == null) {
            return;
        }
        String previous = getSelectedProgrammerValue();
        if (previous.isEmpty()) {
            previous = getPreferenceStore().getString(Mik32PluginPreferences.PROGRAMMER_CONFIG);
        }

        List<File> configs = findProgrammerConfigs(uploaderPathText.getText().trim());
        programmerValues = new String[configs.size()];
        programmerCombo.removeAll();

        for (int index = 0; index < configs.size(); index++) {
            String fileName = configs.get(index).getName();
            programmerValues[index] = fileName;
            programmerCombo.add(fileName.endsWith(".cfg") ? fileName.substring(0, fileName.length() - 4) : fileName);
            if (fileName.equals(previous)) {
                programmerCombo.select(index);
            }
        }

        boolean hasProgrammers = programmerValues.length > 0;
        programmerCombo.setEnabled(hasProgrammers);
        if (hasProgrammers && programmerCombo.getSelectionIndex() < 0) {
            programmerCombo.select(0);
        }
        if (!hasProgrammers) {
            programmerCombo.add("No programmers found");
            programmerCombo.select(0);
        }
    }

    private List<File> findProgrammerConfigs(String uploaderPath) {
        List<File> configs = new ArrayList<>();
        if (uploaderPath.isEmpty()) {
            return configs;
        }

        File root = new File(uploaderPath);
        addCfgFiles(configs, new File(root, "openocd-scripts/interface"));
        addCfgFiles(configs, new File(root, "uploader/openocd-scripts/interface"));
        configs.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return configs;
    }

    private void addCfgFiles(List<File> configs, File directory) {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".cfg"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!containsConfig(configs, file.getName())) {
                configs.add(file);
            }
        }
    }

    private boolean containsConfig(List<File> configs, String fileName) {
        for (File config : configs) {
            if (config.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String getSelectedProgrammerValue() {
        if (programmerCombo == null || programmerValues.length == 0) {
            return "";
        }
        int index = programmerCombo.getSelectionIndex();
        if (index < 0 || index >= programmerValues.length) {
            return "";
        }
        return programmerValues[index];
    }
}
