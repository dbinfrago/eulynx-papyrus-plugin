package com.eulynx.papyrus.specification.statemachine;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Region;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Sync State Machine to Requirements..." command: generates/updates one
 * {@code «Requirement»} per state, transition, and entry/exit/doActivity
 * behavior of the current State Machine Diagram's state machine, via
 * {@link StateMachineRequirementSync} - for Polarion export, matching the
 * PTC/DOORS export text convention.
 */
public class SyncStateMachineRequirementsHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}
		Diagram diagram = part.getDiagram();
		StateMachine stateMachine = diagram == null ? null : resolveStateMachine(diagram.getElement());
		if (stateMachine == null) {
			return null;
		}

		StateMachineRequirementSync sync = new StateMachineRequirementSync();
		Package container = sync.resolveContainerPackage(stateMachine);
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(stateMachine);
		domain.getCommandStack().execute(new RecordingCommand(domain, "Sync State Machine to Requirements") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				sync.sync(stateMachine, container);
			}
		});

		Shell shell = HandlerUtil.getActiveShell(event);
		MessageDialog.openInformation(shell, "Sync State Machine to Requirements", //$NON-NLS-1$
				"Created: " + sync.getCreatedCount() + "\nUpdated: " + sync.getUpdatedCount()); //$NON-NLS-1$ //$NON-NLS-2$
		return null;
	}

	private static StateMachine resolveStateMachine(EObject element) {
		if (element instanceof StateMachine) {
			return (StateMachine) element;
		}
		if (element instanceof Region) {
			return ((Region) element).getStateMachine();
		}
		return null;
	}
}
