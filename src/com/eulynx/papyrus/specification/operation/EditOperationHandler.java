package com.eulynx.papyrus.specification.operation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.uml2.uml.Operation;

/**
 * "Edit Operation Body..." command (Model Explorer and diagrams): opens
 * {@link EditOperationDialog} for the selected Operation.
 */
public class EditOperationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		Operation operation = null;
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			for (Object selected : ((IStructuredSelection) selection).toList()) {
				operation = toOperation(selected);
				if (operation != null) {
					break;
				}
			}
		}
		if (operation == null) {
			MessageDialog.openInformation(shell, "Edit Operation Body", "Select an Operation first."); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(operation);
		EditOperationDialog dialog = new EditOperationDialog(shell, operation);
		if (dialog.open() == Window.OK) {
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Operation Body") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					dialog.applyChanges();
				}
			});
		}
		return null;
	}

	private static Operation toOperation(Object selected) {
		Object candidate = selected;
		if (candidate instanceof EditPart && ((EditPart) candidate).getModel() instanceof View) {
			candidate = ((View) ((EditPart) candidate).getModel()).getElement();
		}
		if (candidate instanceof Operation) {
			return (Operation) candidate;
		}
		// Model Explorer selections are not always raw elements
		Object adapted = candidate instanceof IAdaptable ? ((IAdaptable) candidate).getAdapter(EObject.class) : null;
		if (adapted == null && candidate != null) {
			adapted = Platform.getAdapterManager().getAdapter(candidate, EObject.class);
		}
		return adapted instanceof Operation ? (Operation) adapted : null;
	}
}
