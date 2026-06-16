package mik32.eclipse.toolchain;

import java.net.URL;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.osgi.framework.Bundle;

public final class Mik32AboutDialog extends TitleAreaDialog {
    private Image titleImage;

    public Mik32AboutDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("About / Support MIK32 Extension");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(520, 600);
    }

    @Override
    protected Control createContents(Composite parent) {
        Control contents = super.createContents(parent);
        
        setTitle("О плагине и поддержке разработчика");
        setMessage("MrMarsov MIK32 Projects Tools - информация и поддержка.");
        
        Bundle bundle = Platform.getBundle(Startup.PLUGIN_ID);
        if (bundle != null) {
            URL url = FileLocator.find(bundle, new org.eclipse.core.runtime.Path("icons/mik32_48.png"), null);
            if (url != null) {
                titleImage = ImageDescriptor.createFromURL(url).createImage();
                setTitleImage(titleImage);
            }
        }
        
        return contents;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        
        GridLayout layout = (GridLayout) area.getLayout();
        layout.marginWidth = 20;
        layout.marginHeight = 20;
        layout.verticalSpacing = 10;

        Link link = new Link(area, SWT.WRAP);
        link.setText("Здравствуй, дорогой пользователь! Это расширение было создано, чтобы облегчить процесс разработки для МИК32.\n\n"
            + "В настройках IDE (Окно -> Параметры -> MIK32 или Window -> Preferences -> MIK32) Вы также можете ознакомиться с необходимыми параметрами и выбором программатора.\n\n"
            + "Компания, в которой мы разрабатывали отечественное оборудование и устройства, сейчас на грани из-за давления и отсутствия поддержки со стороны государства. Я сейчас нахожусь в сложном положении и закрываю крупную задолженность, из-за кризиса я остался без работы и на грани депрессии, буду очень рад и благодарен поддержке, даже моральной.\n\n"
            + "Связаться со мной можно по почте: <a href=\"mailto:tim.marsov@gmail.com\">tim.marsov@gmail.com</a>\n\n"
            + "Поддержать финансово и помочь выбраться из долговой ямы:\n"
            + "  • Boosty: <a href=\"https://boosty.to/mrmarsov\">boosty.to/mrmarsov</a>\n"
            + "  • YooMoney: <a href=\"https://yoomoney.ru/fundraise/1IDQG8PSQV7.260615\">yoomoney.ru/fundraise/1IDQG8PSQV7.260615</a>\n\n"
            + "GitHub репозиторий проекта:\n"
            + "  • <a href=\"https://github.com/TimMarsov/MIK32EclipseTools\">github.com/TimMarsov/MIK32EclipseTools</a>");
        
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 480;
        link.setLayoutData(gd);
        link.addListener(SWT.Selection, event -> org.eclipse.swt.program.Program.launch(event.text));

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Закрыть", true);
    }

    @Override
    public boolean close() {
        if (titleImage != null && !titleImage.isDisposed()) {
            titleImage.dispose();
        }
        return super.close();
    }
}
