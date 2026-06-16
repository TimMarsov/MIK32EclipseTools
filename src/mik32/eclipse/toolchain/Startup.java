package mik32.eclipse.toolchain;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IStartup;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class Startup implements IStartup {
    public static final String PLUGIN_ID = "mik32.eclipse.toolchain";

    @Override
    public void earlyStartup() {
        Bundle bundle = FrameworkUtil.getBundle(Startup.class);
        Platform.getLog(bundle).log(new Status(IStatus.INFO, PLUGIN_ID, "MrMarsov MIK32 Eclipse Tools loaded."));
    }
}
