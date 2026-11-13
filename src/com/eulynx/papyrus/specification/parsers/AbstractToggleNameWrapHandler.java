package com.eulynx.papyrus.specification.parsers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.BooleanValueStyle;
import org.eclipse.gmf.runtime.notation.NotationFactory;
import org.eclipse.gmf.runtime.notation.NotationPackage;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Shared logic for the "Wrap Name" / "Unwrap Name" commands: lets the name label
 * of a classifier shape (e.g. a UseCase) wrap onto several lines, so the shape
 * can be made narrower. Writes the boolean named style {@code isNameWrap} on the
 * shape's notation view, which Papyrus's {@code NamedElementEditPart} already
 * reads on every refresh. Works for every classifier shape, not only UseCases.
 */
abstract class AbstractToggleNameWrapHandler extends AbstractDiagramHandler {

	private static final String WRAP_NAME_PROPERTY = "isNameWrap"; //$NON-NLS-1$

	protected abstract boolean wrapName();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		final List<View> targets = new ArrayList<>();
		List<EditPart> targetEditParts = new ArrayList<>();
		for (Object selected : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			EditPart editPart = (EditPart) selected;
			View view = resolveShapeView(editPart);
			if (view != null) {
				targets.add(view);
				targetEditParts.add(editPart);
			}
		}
		if (targets.isEmpty()) {
			return null;
		}

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(targets.get(0));
		domain.getCommandStack().execute(new RecordingCommand(domain, "Set Name Wrap") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				for (View view : targets) {
					setNameWrap(view, wrapName());
				}
			}
		});

		for (EditPart editPart : targetEditParts) {
			editPart.refresh();
		}
		return null;
	}

	/**
	 * Resolves the classifier shape's own {@code View} (the one {@code
	 * refreshLabelDisplay()} reads from) for a selected edit part - either
	 * the selection itself (if the shape was selected directly - its own
	 * {@code View#getElement()} is already a {@code NamedElement}), or its
	 * parent (if the name-label text was selected instead, same as
	 * "Hide/Show Multiplicity" above resolves Port_NameLabel/
	 * Property_NameLabel back to their parent Port_Shape/Property_Shape).
	 */
	private static View resolveShapeView(EditPart editPart) {
		Object model = editPart.getModel();
		if (!(model instanceof View)) {
			return null;
		}
		View view = (View) model;
		if (view.getElement() instanceof NamedElement) {
			return view;
		}
		Object parent = view.eContainer();
		if (parent instanceof View && ((View) parent).getElement() instanceof NamedElement) {
			return (View) parent;
		}
		return null;
	}

	private static void setNameWrap(View view, boolean wrap) {
		BooleanValueStyle style = (BooleanValueStyle) view.getNamedStyle(NotationPackage.eINSTANCE.getBooleanValueStyle(), WRAP_NAME_PROPERTY);
		if (style == null) {
			style = NotationFactory.eINSTANCE.createBooleanValueStyle();
			style.setName(WRAP_NAME_PROPERTY);
			view.getStyles().add(style);
		}
		style.setBooleanValue(wrap);
	}
}
