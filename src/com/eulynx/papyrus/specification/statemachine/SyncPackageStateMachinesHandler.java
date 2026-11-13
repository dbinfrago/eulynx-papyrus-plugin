package com.eulynx.papyrus.specification.statemachine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.StateMachine;

/**
 * "Sync State Machines in Package to Requirements..." (Model Explorer): runs
 * {@link StateMachineRequirementSync} for every State Machine found
 * (recursively) below the selected Package(s), so no State Machine Diagram
 * has to be opened per Block. Each State Machine gets its own
 * "&lt;name&gt; - Requirements" package, exactly like the diagram command.
 */
public class SyncPackageStateMachinesHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<StateMachine> machines = new ArrayList<>();
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			for (Iterator<?> it = ((IStructuredSelection) selection).iterator(); it.hasNext();) {
				Package pkg = toPackage(it.next());
				if (pkg == null) {
					continue;
				}
				for (Iterator<EObject> contents = pkg.eAllContents(); contents.hasNext();) {
					EObject next = contents.next();
					if (next instanceof StateMachine && !machines.contains(next)) {
						machines.add((StateMachine) next);
					}
				}
			}
		}
		Shell shell = HandlerUtil.getActiveShell(event);
		if (machines.isEmpty()) {
			MessageDialog.openInformation(shell, "Sync State Machines", //$NON-NLS-1$
					"No State Machines found in the selected Package."); //$NON-NLS-1$
			return null;
		}

		int[] counts = new int[2];
		StringBuilder names = new StringBuilder();
		for (StateMachine machine : machines) {
			TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(machine);
			if (domain == null) {
				continue;
			}
			StateMachineRequirementSync sync = new StateMachineRequirementSync();
			domain.getCommandStack().execute(new RecordingCommand(domain, "Sync State Machine to Requirements") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					sync.sync(machine, sync.resolveContainerPackage(machine));
				}
			});
			counts[0] += sync.getCreatedCount();
			counts[1] += sync.getUpdatedCount();
			names.append("\n- ").append(machine.getName()); //$NON-NLS-1$
		}
		MessageDialog.openInformation(shell, "Sync State Machines", //$NON-NLS-1$
				"State Machines: " + machines.size() + names + "\n\nCreated: " + counts[0] //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ "\nUpdated: " + counts[1]); //$NON-NLS-1$
		return null;
	}

	private static Package toPackage(Object selected) {
		if (selected instanceof Package) {
			return (Package) selected;
		}
		if (selected instanceof IAdaptable) {
			Object adapted = ((IAdaptable) selected).getAdapter(EObject.class);
			if (adapted instanceof Package) {
				return (Package) adapted;
			}
		}
		Object adapted = Platform.getAdapterManager().getAdapter(selected, EObject.class);
		return adapted instanceof Package ? (Package) adapted : null;
	}
}
