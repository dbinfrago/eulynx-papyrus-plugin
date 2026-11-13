package com.eulynx.papyrus.specification.statemachine.width;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.papyrus.uml.diagram.statemachine.edit.parts.TransitionGuardEditPart;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Set Label Width..." command: prompts for a pixel width and stores it for every
 * selected Transition guard/trigger/effect label, so the label wraps onto more
 * lines to fit a narrow diagram. The stored guard/effect text is never changed;
 * the width is only used for display by
 * {@link WidthAwareTransitionGuardEditPart}.
 */
public class SetLabelWidthHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		List<EditPart> labelParts = new ArrayList<>();
		List<View> labelViews = new ArrayList<>();
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (model instanceof View && TransitionGuardEditPart.VISUAL_ID.equals(((View) model).getType())) {
				labelParts.add(editPart);
				labelViews.add((View) model);
			}
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		if (labelViews.isEmpty()) {
			MessageDialog.openWarning(shell, "Set Label Width", //$NON-NLS-1$
					"Select a transition's guard/trigger/effect label first (click the label text itself, not the transition line)."); //$NON-NLS-1$
			return null;
		}

		IInputValidator validator = input -> {
			try {
				return Integer.parseInt(input.trim()) > 0 ? null : "Enter a positive number."; //$NON-NLS-1$
			} catch (NumberFormatException e) {
				return "Enter a whole number."; //$NON-NLS-1$
			}
		};
		InputDialog dialog = new InputDialog(shell, "Set Label Width", //$NON-NLS-1$
				"Maximum label width in pixels - the label wraps onto multiple lines within it; " //$NON-NLS-1$
						+ "the underlying guard/trigger/effect text itself is never changed:", //$NON-NLS-1$
				"220", validator); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return null;
		}
		int width = Integer.parseInt(dialog.getValue().trim());

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(labelViews.get(0));
		domain.getCommandStack().execute(new RecordingCommand(domain, "Set Label Width") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				for (View view : labelViews) {
					EAnnotation annotation = view.getEAnnotation(WidthAwareTransitionGuardEditPart.ANNOTATION_SOURCE);
					if (annotation == null) {
						annotation = EcoreFactory.eINSTANCE.createEAnnotation();
						annotation.setSource(WidthAwareTransitionGuardEditPart.ANNOTATION_SOURCE);
						view.getEAnnotations().add(annotation);
					}
					annotation.getDetails().put(WidthAwareTransitionGuardEditPart.WIDTH_KEY, String.valueOf(width));
				}
			}
		});

		for (EditPart editPart : labelParts) {
			editPart.refresh();
		}
		return null;
	}
}
