package com.eulynx.papyrus.specification.timing;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.uml2.uml.DurationConstraint;

/**
 * "Edit Duration Arrow..." (context menu): operator/expression dialog for the
 * selected duration between two events (the arrow itself or its label).
 */
public class EditDurationArrowHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (model instanceof View && DurationLinkSupport.hasData(((View) model).getElement())) {
				DurationLinkSupport.edit(HandlerUtil.getActiveShell(event), (DurationConstraint) ((View) model).getElement());
				break;
			}
		}
		return null;
	}
}
