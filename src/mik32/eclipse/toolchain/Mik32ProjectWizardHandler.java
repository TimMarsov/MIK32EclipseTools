package mik32.eclipse.toolchain;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.PlatformUI;

public final class Mik32ProjectWizardHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Mik32ProjectWizard wizard = new Mik32ProjectWizard(Mik32ProjectWizard.ProjectMode.MANAGED);
        wizard.init(PlatformUI.getWorkbench(), null);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.open();
        return null;
    }
}
