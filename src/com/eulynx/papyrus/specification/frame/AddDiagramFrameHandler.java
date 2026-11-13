// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.frame;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;

/**
 * "Add Diagram Frame" command, available from the diagram background's
 * context menu: inserts a {@link DiagramFrameEditPart} node into the
 * currently active diagram. Works the same for brand new and pre-existing
 * Use Case, Block Definition, Internal Block and State Machine diagrams -
 * there is no separate auto-insert-on-creation path (that would have
 * required registering a second, SysML-context-incompatible "representation
 * kind" via Papyrus's Architecture Framework, which turned out to make the
 * stock SysML diagram kinds unselectable in the same model - not worth the
 * added complexity). The "kind" prefix is guessed from the diagram's
 * notation type (best-effort, since e.g. a plain Class Diagram and a SysML
 * Block Definition Diagram share the same notation type).
 */
public class AddDiagramFrameHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		if (part == null) {
			return null;
		}
		final Diagram diagram = part.getDiagram();
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(diagram);

		domain.getCommandStack().execute(new RecordingCommand(domain, "Add Diagram Frame") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				DiagramFrameInserter.insertFrame(diagram, guessKindPrefix(diagram));
			}
		});
		return null;
	}

	private String guessKindPrefix(Diagram diagram) {
		String type = diagram.getType();
		if ("UseCase".equals(type)) { //$NON-NLS-1$
			return "uc"; //$NON-NLS-1$
		}
		if ("PapyrusUMLClassDiagram".equals(type)) { //$NON-NLS-1$
			// Plain Class Diagram and SysML BDD share this same notation
			// type; "bdd" is the more likely target for this command.
			return "bdd"; //$NON-NLS-1$
		}
		if ("CompositeStructure".equals(type)) { //$NON-NLS-1$
			// Plain Composite Structure Diagram and SysML IBD share this
			// same notation type; "ibd" is the more likely target for this
			// command.
			return "ibd"; //$NON-NLS-1$
		}
		if ("PapyrusUMLStateMachineDiagram".equals(type)) { //$NON-NLS-1$
			return "stm"; //$NON-NLS-1$
		}
		return "frame"; //$NON-NLS-1$
	}
}
