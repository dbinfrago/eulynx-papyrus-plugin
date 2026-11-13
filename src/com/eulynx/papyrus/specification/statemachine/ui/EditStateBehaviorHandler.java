package com.eulynx.papyrus.specification.statemachine.ui;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.window.Window;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.State;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Edit State Behavior..." command: opens {@link EditStateBehaviorDialog} for
 * the selected state's entry/exit/doActivity and internal transitions.
 */
public class EditStateBehaviorHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		State state = null;
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (!(model instanceof View)) {
				continue;
			}
			View view = (View) model;
			if (view.getElement() instanceof State) {
				state = (State) view.getElement();
				break;
			}
		}
		if (state == null) {
			return null;
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(state);
		EditStateBehaviorDialog dialog = new EditStateBehaviorDialog(shell, state);
		if (dialog.open() == Window.OK) {
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit State Behavior") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					dialog.applyChanges();
				}
			});
		}
		return null;
	}
}
