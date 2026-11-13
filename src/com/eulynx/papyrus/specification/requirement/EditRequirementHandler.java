// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.requirement;

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
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Edit Requirement..." command: opens {@link EditRequirementDialog} for the
 * selected {@code «Requirement»}-stereotyped element, as a faster route to
 * its {@code id}/{@code text} than the generic Profile-tab stereotype tree.
 */
public class EditRequirementHandler extends AbstractDiagramHandler {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		Element requirement = null;
		Stereotype stereotype = null;
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (!(model instanceof View)) {
				continue;
			}
			View view = (View) model;
			if (view.getElement() instanceof Element) {
				Element element = (Element) view.getElement();
				Stereotype applied = element.getAppliedStereotype(REQUIREMENT_STEREOTYPE);
				if (applied != null) {
					requirement = element;
					stereotype = applied;
					break;
				}
			}
		}
		if (requirement == null) {
			return null;
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(requirement);
		EditRequirementDialog dialog = new EditRequirementDialog(shell, requirement, stereotype);
		if (dialog.open() == Window.OK) {
			Element finalRequirement = requirement;
			Stereotype finalStereotype = stereotype;
			String newId = dialog.getRequirementId();
			String newText = dialog.getRequirementText();
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Requirement") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					finalRequirement.setValue(finalStereotype, "id", newId); //$NON-NLS-1$
					finalRequirement.setValue(finalStereotype, "text", newText); //$NON-NLS-1$
				}
			});
		}
		return null;
	}
}
