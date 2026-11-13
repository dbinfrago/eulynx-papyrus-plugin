package com.eulynx.papyrus.specification.parsers;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.parsers.OpaqueBehaviorViewUtil;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.parsers.TransitionPropertiesParser;
import org.eclipse.papyrus.uml.internationalization.utils.utils.UMLLabelInternationalization;
import org.eclipse.uml2.uml.ChangeEvent;
import org.eclipse.uml2.uml.OpaqueExpression;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.SignalEvent;
import org.eclipse.uml2.uml.Signal;
import org.eclipse.uml2.uml.CallEvent;
import org.eclipse.uml2.uml.Event;
import org.eclipse.uml2.uml.TimeEvent;
import org.eclipse.uml2.uml.TimeExpression;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.Trigger;
import org.eclipse.uml2.uml.ValueSpecification;

/**
 * Text der Trigger einer Transition mit dem fehlenden "when (...)" fuer
 * ChangeEvents (UML-Notation), analog zum vorhandenen "after "/"at " bei
 * TimeEvent. Gemeinsam genutzt von {@link CustomTransitionPropertiesParser}
 * (Transitionen zwischen States) und {@link CustomInternalTransitionParser}
 * (interne Transitionen im State-Kompartiment) - beide haben in Papyrus
 * eigene Parser-Klassen, die den Trigger-Text getrennt aufbauen.
 */
public final class TransitionTriggerText {

	private TransitionTriggerText() {
	}

	public static String getTextForTrigger(View view, Transition trans) {
		StringBuilder result = new StringBuilder();
		boolean isFirstTrigger = true;
		for (Trigger t : trans.getTriggers()) {
			if (t != null) {
				if (!isFirstTrigger) {
					result.append(", "); //$NON-NLS-1$
				} else {
					isFirstTrigger = false;
				}
				Event e = t.getEvent();
				if (e instanceof CallEvent) {
					Operation op = ((CallEvent) e).getOperation();
					if (op != null) {
						result.append(UMLLabelInternationalization.getInstance().getLabel(op));
						if ((op.getOwnedParameters().size() > 0) && OpaqueBehaviorViewUtil.displayParamDots(view)) {
							result.append(OpaqueBehaviorViewUtil.PARAM_DOTS);
						}
					} else {
						result.append(UMLLabelInternationalization.getInstance().getLabel((e)));
					}
				} else if (e instanceof SignalEvent) {
					Signal signal = ((SignalEvent) e).getSignal();
					if (signal != null) {
						result.append(UMLLabelInternationalization.getInstance().getLabel(signal));
						if ((signal.getAttributes().size() > 0) && OpaqueBehaviorViewUtil.displayParamDots(view)) {
							result.append(OpaqueBehaviorViewUtil.PARAM_DOTS);
						}
					} else {
						result.append(UMLLabelInternationalization.getInstance().getLabel((e)));
					}
				} else if (e instanceof ChangeEvent) {
					// --- HIER die Korrektur: "when (...)" statt nur des rohen Ausdrucks ---
					ValueSpecification vs = ((ChangeEvent) e).getChangeExpression();
					String value;
					if (vs instanceof OpaqueExpression) {
						value = OpaqueBehaviorViewUtil.retrieveBody(view, (OpaqueExpression) vs);
					} else {
						value = vs.stringValue();
					}
					result.append("when ("); //$NON-NLS-1$
					result.append(value);
					result.append(")"); //$NON-NLS-1$
				} else if (e instanceof TimeEvent) {
					TimeEvent timeEvent = (TimeEvent) e;
					result.append(timeEvent.isRelative() ? "after (" : "at ("); //$NON-NLS-1$ //$NON-NLS-2$
					TimeExpression te = timeEvent.getWhen();
					String value;
					if (te != null) {
						ValueSpecification vs = te.getExpr();
						if (vs instanceof OpaqueExpression) {
							value = OpaqueBehaviorViewUtil.retrieveBody(view, (OpaqueExpression) vs);
						} else {
							value = vs.stringValue();
						}
					} else {
						value = "undefined"; //$NON-NLS-1$
					}
					result.append(value);
					result.append(")"); //$NON-NLS-1$
				} else {
					result.append("all"); //$NON-NLS-1$
				}
			}
		}
		return result.toString();
	}
}
