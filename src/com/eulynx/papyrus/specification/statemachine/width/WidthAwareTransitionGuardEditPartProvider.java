package com.eulynx.papyrus.specification.statemachine.width;

import org.eclipse.gmf.runtime.diagram.ui.services.editpart.AbstractEditPartProvider;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.edit.parts.TransitionGuardEditPart;

/**
 * Supplies {@link WidthAwareTransitionGuardEditPart} for every Transition
 * guard/trigger/effect label ({@link TransitionGuardEditPart#VISUAL_ID}),
 * registered with Priority "Highest" (see {@code plugin.xml}) so it's
 * selected ahead of Papyrus's own provider for that view type - same
 * pattern already used by {@code DiagramFrameEditPartProvider}.
 */
public class WidthAwareTransitionGuardEditPartProvider extends AbstractEditPartProvider {

	@Override
	protected Class getNodeEditPartClass(View view) {
		if (TransitionGuardEditPart.VISUAL_ID.equals(view.getType())) {
			return WidthAwareTransitionGuardEditPart.class;
		}
		return null;
	}
}
