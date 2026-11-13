package com.eulynx.papyrus.specification.parsers;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.gmf.runtime.common.core.service.AbstractProvider;
import org.eclipse.gmf.runtime.common.core.service.IOperation;
import org.eclipse.gmf.runtime.common.ui.services.parser.GetParserOperation;
import org.eclipse.gmf.runtime.common.ui.services.parser.IParser;
import org.eclipse.gmf.runtime.common.ui.services.parser.IParserProvider;
import org.eclipse.papyrus.uml.diagram.statemachine.edit.parts.InternalTransitionEditPart;
import org.eclipse.papyrus.uml.diagram.statemachine.edit.parts.TransitionGuardEditPart;

/**
 * Registriert unsere Parser als Ersatz fuer die Standard-Parser der
 * Transition-Beschriftungen:
 * <ul>
 * <li>{@link CustomTransitionPropertiesParser} fuer die Guard/Trigger-
 * Beschriftung einer Transition zwischen States
 * ({@link TransitionGuardEditPart}),</li>
 * <li>{@link CustomInternalTransitionParser} fuer die Zeilen der internen
 * Transitionen im State-Kompartiment ({@link InternalTransitionEditPart}).</li>
 * </ul>
 * Muss mit Priority "Highest" registriert werden, damit er vor dem
 * Standard-UMLParserProvider (Priority "Lowest") zum Zug kommt.
 */
public class CustomTransitionParserProvider extends AbstractProvider implements IParserProvider {

	private final IParser customParser = new CustomTransitionPropertiesParser();

	private final IParser customInternalParser = new CustomInternalTransitionParser();

	@Override
	public IParser getParser(IAdaptable hint) {
		String visualId = hint.getAdapter(String.class);
		if (InternalTransitionEditPart.VISUAL_ID.equals(visualId)) {
			return customInternalParser;
		}
		return customParser;
	}

	@Override
	public boolean provides(IOperation operation) {
		if (operation instanceof GetParserOperation) {
			IAdaptable hint = ((GetParserOperation) operation).getHint();
			String visualId = hint.getAdapter(String.class);
			return TransitionGuardEditPart.VISUAL_ID.equals(visualId) || InternalTransitionEditPart.VISUAL_ID.equals(visualId);
		}
		return false;
	}
}
