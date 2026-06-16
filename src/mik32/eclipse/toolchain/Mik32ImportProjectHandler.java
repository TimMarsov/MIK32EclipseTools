package mik32.eclipse.toolchain;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.wizards.datatransfer.ExternalProjectImportWizard;
import org.eclipse.jface.wizard.WizardDialog;

public final class Mik32ImportProjectHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ExternalProjectImportWizard wizard = new ExternalProjectImportWizard();
        wizard.init(PlatformUI.getWorkbench(), null);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.open();
        return null;
    }
}
