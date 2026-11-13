package com.eulynx.papyrus.specification.timing;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.uml2.uml.Interaction;

/**
 * "Add Duration Between Events..." (Sequence Diagram background context menu):
 * pick the start and end event from the messages/executions shown in the
 * diagram, an operator and an expression; creates a DurationConstraint with
 * Papyrus' own duration link between the two.
 */
public class AddDurationLinkHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}
		final Diagram diagram = part.getDiagram();
		Shell shell = HandlerUtil.getActiveShell(event);

		EObject element = diagram.getElement();
		if (!(element instanceof Interaction)) {
			MessageDialog.openInformation(shell, "Duration Between Events", //$NON-NLS-1$
					"This command works in Sequence Diagrams (the diagram has to belong to an Interaction)."); //$NON-NLS-1$
			return null;
		}
		final Interaction interaction = (Interaction) element;

		final List<DurationLinkSupport.Endpoint> endpoints = DurationLinkSupport.collectEndpoints(diagram, interaction);
		if (endpoints.size() < 2) {
			MessageDialog.openInformation(shell, "Duration Between Events", //$NON-NLS-1$
					"The diagram needs at least two message ends or executions to attach a duration to."); //$NON-NLS-1$
			return null;
		}

		List<String> labels = new ArrayList<>();
		for (DurationLinkSupport.Endpoint endpoint : endpoints) {
			labels.add(endpoint.label);
		}
		DurationArrowDialog dialog = new DurationArrowDialog(shell, interaction, "", "", labels, 0, 1); //$NON-NLS-1$ //$NON-NLS-2$
		if (dialog.open() != Window.OK) {
			return null;
		}
		final DurationLinkSupport.Endpoint start = endpoints.get(dialog.getStartIndex());
		final DurationLinkSupport.Endpoint end = endpoints.get(dialog.getEndIndex());
		final String operator = dialog.getOperator();
		final String expression = dialog.getExpressionHtml();

		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(diagram);
		domain.getCommandStack().execute(new RecordingCommand(domain, "Add Duration Between Events") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				DurationLinkSupport.create(diagram, interaction, start, end, operator, expression);
			}
		});
		return null;
	}
}
