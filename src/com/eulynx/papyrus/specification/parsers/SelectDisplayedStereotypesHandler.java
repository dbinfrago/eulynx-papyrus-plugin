package com.eulynx.papyrus.specification.parsers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Element;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Select Displayed Stereotypes..." command: opens
 * {@link SelectDisplayedStereotypesDialog} for every selected element that
 * has at least one applied stereotype (supports multi-selection).
 */
public class SelectDisplayedStereotypesHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		Map<Element, View> targets = new LinkedHashMap<>();
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (!(model instanceof View)) {
				continue;
			}
			View view = (View) model;
			if (view.getElement() instanceof Element) {
				Element element = (Element) view.getElement();
				if (!element.getAppliedStereotypes().isEmpty()) {
					targets.put(element, view);
				}
			}
		}
		if (targets.isEmpty()) {
			return null;
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(targets.values().iterator().next());
		new SelectDisplayedStereotypesDialog(shell, domain, targets).open();
		return null;
	}
}
