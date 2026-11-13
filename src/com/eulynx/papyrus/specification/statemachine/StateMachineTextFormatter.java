package com.eulynx.papyrus.specification.statemachine;

import java.util.List;

import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.CallEvent;
import org.eclipse.uml2.uml.ChangeEvent;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Event;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.OpaqueExpression;
import org.eclipse.uml2.uml.Signal;
import org.eclipse.uml2.uml.SignalEvent;
import org.eclipse.uml2.uml.State;
import org.eclipse.uml2.uml.TimeEvent;
import org.eclipse.uml2.uml.TimeExpression;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.TransitionKind;
import org.eclipse.uml2.uml.Trigger;
import org.eclipse.uml2.uml.ValueSpecification;
import org.eclipse.uml2.uml.Vertex;

/**
 * Pure text-formatting logic (no model mutation) for turning State Machine
 * elements into the text convention the existing PTC/DOORS export used, e.g.
 * {@code when(<trigger>)[<guard>]/<effect>{<source> - <target>}} for an external
 * transition, or {@code entry/<body>{State-internal in <state>}} for a state's
 * entry behavior. Trigger-text extraction mirrors {@code
 * com.eulynx.papyrus.specification.parsers.
 * TransitionTriggerText#getTextForTrigger}, but without that class's
 * {@code View} dependency, since this runs offline rather than during
 * diagram-label rendering.
 */
public final class StateMachineTextFormatter {

	private StateMachineTextFormatter() {
	}

	/** A state/pseudostate's own name, as-is. */
	public static String textFor(Vertex vertex) {
		return vertex.getName();
	}

	/** {@code when(...)[...]/effect{source - target}}, or {@code .../effect{State-internal in state}} for an internal transition. */
	public static String textFor(Transition transition) {
		StringBuilder result = new StringBuilder();

		List<Trigger> triggers = transition.getTriggers();
		if (!triggers.isEmpty()) {
			StringBuilder triggerText = new StringBuilder();
			for (int i = 0; i < triggers.size(); i++) {
				if (i > 0) {
					triggerText.append(", "); //$NON-NLS-1$
				}
				triggerText.append(textForTrigger(triggers.get(i)));
			}
			result.append(triggerText);
		}

		Constraint guard = transition.getGuard();
		if (guard != null) {
			result.append('[');
			result.append(textForValueSpecification(guard.getSpecification()));
			result.append(']');
		}

		result.append('/');
		result.append(textForBehavior(transition.getEffect()));

		if (transition.getKind() == TransitionKind.INTERNAL_LITERAL) {
			result.append("{State-internal in "); //$NON-NLS-1$
			result.append(nameOrEmpty(transition.getSource()));
			result.append('}');
		} else {
			result.append('{');
			result.append(nameOrEmpty(transition.getSource()));
			result.append(" - "); //$NON-NLS-1$
			result.append(nameOrEmpty(transition.getTarget()));
			result.append('}');
		}
		return result.toString();
	}

	/** {@code entry/body{State-internal in state}} (or {@code exit}/{@code do}). */
	public static String textForStateBehavior(State state, String kindLabel, Behavior behavior) {
		return kindLabel + "/" + textForBehavior(behavior) + "{State-internal in " + nameOrEmpty(state) + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String textForTrigger(Trigger trigger) {
		Event event = trigger.getEvent();
		if (event instanceof ChangeEvent) {
			return "when (" + textForValueSpecification(((ChangeEvent) event).getChangeExpression()) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (event instanceof TimeEvent) {
			TimeEvent timeEvent = (TimeEvent) event;
			TimeExpression when = timeEvent.getWhen();
			String value = when == null ? "undefined" : textForValueSpecification(when.getExpr()); //$NON-NLS-1$
			return (timeEvent.isRelative() ? "after (" : "at (") + value + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (event instanceof SignalEvent) {
			Signal signal = ((SignalEvent) event).getSignal();
			return signal != null ? nameOrEmpty(signal) : nameOrEmpty(event);
		}
		if (event instanceof CallEvent) {
			Operation operation = ((CallEvent) event).getOperation();
			return operation != null ? nameOrEmpty(operation) : nameOrEmpty(event);
		}
		return "all"; //$NON-NLS-1$
	}

	/**
	 * Public (not just private) since {@code statemachine.ui}'s editors (a
	 * different package) need the exact same text-extraction rules when
	 * populating their fields from an existing Trigger/Guard/Effect.
	 */
	public static String textForValueSpecification(ValueSpecification valueSpecification) {
		if (valueSpecification == null) {
			return ""; //$NON-NLS-1$
		}
		if (valueSpecification instanceof OpaqueExpression) {
			List<String> bodies = ((OpaqueExpression) valueSpecification).getBodies();
			return bodies.isEmpty() ? "" : bodies.get(0); //$NON-NLS-1$
		}
		String value = valueSpecification.stringValue();
		return value == null ? "" : value; //$NON-NLS-1$
	}

	public static String textForBehavior(Behavior behavior) {
		if (behavior instanceof OpaqueBehavior) {
			List<String> bodies = ((OpaqueBehavior) behavior).getBodies();
			return bodies.isEmpty() ? "" : bodies.get(0); //$NON-NLS-1$
		}
		return behavior != null ? nameOrEmpty(behavior) : ""; //$NON-NLS-1$
	}

	public static String nameOrEmpty(NamedElement element) {
		if (element == null) {
			return ""; //$NON-NLS-1$
		}
		String name = element.getName();
		return name == null ? "" : name; //$NON-NLS-1$
	}
}
