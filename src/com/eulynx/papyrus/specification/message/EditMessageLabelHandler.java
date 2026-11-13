package com.eulynx.papyrus.specification.message;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.uml2.uml.Message;

/**
 * "Edit Message Label..." (Kontextmenü im Sequenzdiagramm): Label einer
 * Message als Rich-Text mit Referenzen. Der Text steht im Namen der Message;
 * Umbenennungen der Referenzen zieht {@code RichTextReferenceSync} nach.
 */
public class EditMessageLabelHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}
		Message message = null;
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (model instanceof View && ((View) model).getElement() instanceof Message) {
				message = (Message) ((View) model).getElement();
				break;
			}
		}
		if (message == null) {
			return null;
		}
		Shell shell = HandlerUtil.getActiveShell(event);

		EditMessageLabelDialog dialog = new EditMessageLabelDialog(shell, message);
		if (dialog.open() != Window.OK) {
			return null;
		}
		if ((message.getSignature() != null || !message.getArguments().isEmpty())
				&& !MessageDialog.openConfirm(shell, "Edit Message Label", //$NON-NLS-1$
						"Die Message hat eine Signature bzw. Argumente. Diese werden entfernt, damit Papyrus den Text " //$NON-NLS-1$
								+ "statt des Signal-Aufrufs anzeigt. Fortfahren?")) { //$NON-NLS-1$
			return null;
		}

		String html = dialog.getHtml();
		Message target = message;
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(target);
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Message Label") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				MessageLabelSupport.apply(target, html);
			}
		});
		return null;
	}
}
