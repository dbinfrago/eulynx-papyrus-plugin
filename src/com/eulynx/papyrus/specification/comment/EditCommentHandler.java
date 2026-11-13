package com.eulynx.papyrus.specification.comment;

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
import org.eclipse.uml2.uml.Comment;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Edit Comment..." command: opens {@link EditCommentDialog} for the selected
 * Comment, with the same Bold/Italic/Bullet-list/Insert-Reference editor used
 * for Requirement text.
 */
public class EditCommentHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}

		Comment comment = null;
		for (EditPart editPart : part.getDiagramGraphicalViewer().getSelectedEditParts()) {
			Object model = editPart.getModel();
			if (!(model instanceof View)) {
				continue;
			}
			View view = (View) model;
			if (view.getElement() instanceof Comment) {
				comment = (Comment) view.getElement();
				break;
			}
		}
		if (comment == null) {
			return null;
		}

		Shell shell = HandlerUtil.getActiveShell(event);
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(comment);
		EditCommentDialog dialog = new EditCommentDialog(shell, comment);
		if (dialog.open() == Window.OK) {
			Comment finalComment = comment;
			String newBody = dialog.getCommentBody();
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Comment") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					finalComment.setBody(newBody);
				}
			});
		}
		return null;
	}
}
