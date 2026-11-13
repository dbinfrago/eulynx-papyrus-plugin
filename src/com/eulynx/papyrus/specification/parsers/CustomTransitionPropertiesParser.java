// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.parsers.TransitionPropertiesParser;
import org.eclipse.uml2.uml.Transition;

/**
 * Erweiterung des Standard-Papyrus-Parsers: fuegt fuer ChangeEvent-Trigger
 * (UML-Notation "when (...)") das fehlende Schluesselwort "when" hinzu,
 * analog zum bereits vorhandenen "after "/"at " bei TimeEvent. Die eigentliche
 * Logik steht in {@link TransitionTriggerText}.
 */
public class CustomTransitionPropertiesParser extends TransitionPropertiesParser {

	@Override
	protected String getTextForTrigger(View view, Transition trans) {
		return TransitionTriggerText.getTextForTrigger(view, trans);
	}
}
