package com.eulynx.papyrus.specification.frame;

import org.eclipse.gmf.runtime.diagram.ui.services.editpart.AbstractEditPartProvider;
import org.eclipse.gmf.runtime.notation.View;

/**
 * Supplies {@link DiagramFrameEditPart} for any notation node whose
 * View#getType() is {@link DiagramFrameEditPart#VISUAL_ID}, regardless of
 * which diagram it appears in (the type string is unique to this plugin, so
 * no further scoping by diagram kind is needed).
 */
public class DiagramFrameEditPartProvider extends AbstractEditPartProvider {

	@Override
	protected Class getNodeEditPartClass(View view) {
		if (DiagramFrameEditPart.VISUAL_ID.equals(view.getType())) {
			return DiagramFrameEditPart.class;
		}
		return null;
	}
}
