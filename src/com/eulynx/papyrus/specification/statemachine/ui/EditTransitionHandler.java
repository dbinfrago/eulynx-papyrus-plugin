// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.statemachine.ui;

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
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.TransitionKind;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Edit Transition..." command: opens {@link EditTransitionDialog} for the
 * selected (external) transition. Internal transitions are edited via "Edit
 * State Behavior..." instead, since they're not their own diagram shape - if
 * one is selected here, a short hint says so instead of silently doing nothing.
 */
public class EditTransitionHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		Transition transition = null;
		boolean internalSelected = false;
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (!(model instanceof View)) {
				continue;
			}
			View view = (View) model;
			if (view.getElement() instanceof Transition) {
				Transition candidate = (Transition) view.getElement();
				if (candidate.getKind() != TransitionKind.INTERNAL_LITERAL) {
					transition = candidate;
					break;
				}
				internalSelected = true;
			}
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		if (transition == null) {
			if (internalSelected) {
				MessageDialog.openInformation(shell, "Edit Transition", //$NON-NLS-1$
						"Internal transitions of a state are edited via \"Edit State Behavior...\" " //$NON-NLS-1$
								+ "(select the state itself)."); //$NON-NLS-1$
			}
			return null;
		}

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(transition);
		EditTransitionDialog dialog = new EditTransitionDialog(shell, transition);
		if (dialog.open() == Window.OK) {
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Transition") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					dialog.applyChanges();
				}
			});
		}
		return null;
	}
}
