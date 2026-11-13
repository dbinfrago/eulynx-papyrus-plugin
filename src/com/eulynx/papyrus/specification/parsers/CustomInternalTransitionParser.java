// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.parsers.InternalTransitionParser;
import org.eclipse.uml2.uml.Transition;

/**
 * Wie {@link CustomTransitionPropertiesParser}, aber fuer die Zeilen der
 * internen Transitionen im State-Kompartiment: Papyrus beschriftet die ueber
 * einen eigenen Parser ({@link InternalTransitionParser}), der den
 * Trigger-Text getrennt aufbaut - dort fehlte das "when (...)" bei
 * ChangeEvent-Triggern.
 */
public class CustomInternalTransitionParser extends InternalTransitionParser {

	@Override
	protected String getTextForTrigger(View view, Transition trans) {
		return TransitionTriggerText.getTextForTrigger(view, trans);
	}
}
