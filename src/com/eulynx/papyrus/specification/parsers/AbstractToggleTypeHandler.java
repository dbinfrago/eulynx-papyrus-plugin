package com.eulynx.papyrus.specification.parsers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.infra.gmfdiag.common.helper.MaskLabelHelper;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.papyrus.uml.tools.utils.ICustomAppearance;
import org.eclipse.uml2.uml.Port;
import org.eclipse.uml2.uml.Property;

/**
 * Shared logic for the "Hide/Show Type" commands: same mask mechanism as
 * {@link AbstractToggleMultiplicityHandler} (see there for where the mask has to
 * be written), toggling {@link ICustomAppearance#DISP_TYPE} instead of
 * {@link ICustomAppearance#DISP_MULTIPLICITY}. Hides e.g. the "String" in
 * "d7in_Detection_Element_Status: String".
 */
abstract class AbstractToggleTypeHandler extends AbstractDiagramHandler {

	private static final String PORT_NAME_LABEL_TYPE = "Port_NameLabel"; //$NON-NLS-1$

	private static final String PROPERTY_NAME_LABEL_TYPE = "Property_NameLabel"; //$NON-NLS-1$

	protected abstract boolean showType();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		final List<View> targets = new ArrayList<>();
		for (EditPart selected : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			collectMaskedShapeViews(selected, targets);
		}
		if (targets.isEmpty()) {
			return null;
		}

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(targets.get(0));
		domain.getCommandStack().execute(new RecordingCommand(domain, "Set Type Visibility") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				for (View view : targets) {
					Collection<String> base = view.getElement() instanceof Port ? ICustomAppearance.DEFAULT_UML_PORT
							: ICustomAppearance.DEFAULT_UML_PROPERTY;
					// Only flip the type flag on top of whatever is already stored (see the same comment
					// in AbstractToggleMultiplicityHandler) - otherwise this undoes "Hide Multiplicity".
					Collection<String> current = MaskLabelHelper.getMaskValues(view);
					Collection<String> mask = new ArrayList<>(current != null ? current : base);
					if (showType()) {
						if (!mask.contains(ICustomAppearance.DISP_TYPE)) {
							mask.add(ICustomAppearance.DISP_TYPE);
						}
					} else {
						mask.remove(ICustomAppearance.DISP_TYPE);
					}
					MaskLabelHelper.setMaskValues(view, mask);
				}
			}
		});
		return null;
	}

	/** Same resolution as {@link AbstractToggleMultiplicityHandler#collectMaskedShapeViews}. */
	private void collectMaskedShapeViews(EditPart editPart, List<View> result) {
		Object model = editPart.getModel();
		if (!(model instanceof View)) {
			return;
		}
		View view = (View) model;
		if (view.getElement() instanceof Property) {
			// Port extends Property, so this covers both.
			result.add(view);
			return;
		}
		String type = view.getType();
		if (PORT_NAME_LABEL_TYPE.equals(type) || PROPERTY_NAME_LABEL_TYPE.equals(type)) {
			Object parent = view.eContainer();
			if (parent instanceof View) {
				result.add((View) parent);
			}
		}
	}
}
