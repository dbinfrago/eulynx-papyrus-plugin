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
 * Shared logic for the "Hide/Show Multiplicity" commands, covering SysML flow
 * ports (notation type "Port_NameLabel") and nested block/part properties
 * (notation type "Property_NameLabel") in Internal Block Diagrams.
 * <p>
 * These labels are not produced by GMF's pluggable parser but by the
 * mask-managed edit policies ({@code IndirectPortLabelEditPolicy}/
 * {@code IndirectPropertyLabelEditPolicy}), which show the multiplicity unless
 * the per-view mask preference ({@code MaskLabelHelper}) says otherwise. This
 * class writes that preference.
 * <p>
 * The mask is stored on the <i>parent</i> shape view (Port_Shape/Property_Shape),
 * not on the name-label view: the edit policies look it up there.
 */
abstract class AbstractToggleMultiplicityHandler extends AbstractDiagramHandler {

	private static final String PORT_NAME_LABEL_TYPE = "Port_NameLabel"; //$NON-NLS-1$

	private static final String PROPERTY_NAME_LABEL_TYPE = "Property_NameLabel"; //$NON-NLS-1$

	protected abstract boolean showMultiplicity();

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
		domain.getCommandStack().execute(new RecordingCommand(domain, "Set Multiplicity Visibility") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				for (View view : targets) {
					Collection<String> base = view.getElement() instanceof Port
							? ICustomAppearance.DEFAULT_UML_PORT
							: ICustomAppearance.DEFAULT_UML_PROPERTY;
					// Start from the mask already stored on the view (if any), so this only flips the
					// multiplicity flag - "Hide/Show Type" shares the same mask, and starting from the
					// defaults every time made each command undo the other's setting.
					Collection<String> current = MaskLabelHelper.getMaskValues(view);
					Collection<String> mask = new ArrayList<>(current != null ? current : base);
					if (showMultiplicity()) {
						if (!mask.contains(ICustomAppearance.DISP_MULTIPLICITY)) {
							mask.add(ICustomAppearance.DISP_MULTIPLICITY);
						}
					} else {
						mask.remove(ICustomAppearance.DISP_MULTIPLICITY);
					}
					MaskLabelHelper.setMaskValues(view, mask);
				}
			}
		});
		return null;
	}

	/**
	 * Resolves the Port_Shape/Property_Shape view(s) for a selected edit
	 * part - i.e. the view the relevant "Indirect...LabelEditPolicy" actually
	 * reads the mask from (see class comment): either the selected view
	 * itself (if the port/part glyph was selected directly - its own
	 * View#getElement() is a Port or Property), or its parent (if the label
	 * text was selected instead).
	 */
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
