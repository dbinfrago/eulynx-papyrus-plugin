package com.eulynx.papyrus.specification.statemachine.region;

import org.eclipse.gmf.runtime.diagram.ui.services.editpart.AbstractEditPartProvider;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.edit.parts.RegionCompartmentEditPart;

/**
 * Supplies {@link NamedRegionCompartmentEditPart} for every region
 * compartment ({@link RegionCompartmentEditPart#VISUAL_ID}), registered with
 * Priority "Highest" (see {@code plugin.xml}) so it's selected ahead of
 * Papyrus's own provider - same pattern as
 * {@code WidthAwareTransitionGuardEditPartProvider}.
 */
public class NamedRegionCompartmentEditPartProvider extends AbstractEditPartProvider {

	@Override
	protected Class getNodeEditPartClass(View view) {
		if (RegionCompartmentEditPart.VISUAL_ID.equals(view.getType())) {
			return NamedRegionCompartmentEditPart.class;
		}
		return null;
	}
}
