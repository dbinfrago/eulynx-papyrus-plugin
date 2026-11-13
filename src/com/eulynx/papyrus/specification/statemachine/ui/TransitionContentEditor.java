package com.eulynx.papyrus.specification.statemachine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.eulynx.papyrus.specification.richtext.InsertReferenceDialog;
import com.eulynx.papyrus.specification.richtext.TextReferences;
import com.eulynx.papyrus.specification.richtext.TextReferences.Ref;
import com.eulynx.papyrus.specification.richtext.TextReferences.Value;
import com.eulynx.papyrus.specification.statemachine.StateMachineTextFormatter;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.CallEvent;
import org.eclipse.uml2.uml.ChangeEvent;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Event;
import org.eclipse.uml2.uml.LiteralString;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Signal;
import org.eclipse.uml2.uml.SignalEvent;
import org.eclipse.uml2.uml.TimeEvent;
import org.eclipse.uml2.uml.TimeExpression;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.Trigger;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.ValueSpecification;

/**
 * Reusable trigger-list + guard + effect editor for a {@link Transition}
 * (external or internal - both have the same shape). Each trigger row has an
 * event-kind selector (Change/Signal/Call/Time) and one text field whose
 * *meaning* depends on the selected kind (condition text / Signal name /
 * Operation name / time expression) - kept uniform across kinds rather than a
 * dedicated widget per kind, each with its own "Insert Reference..." button
 * (opens {@link InsertReferenceDialog}, the same element picker used for
 * Requirement/Comment rich text). The picked element's name is inserted as a
 * <i>live reference</i> ({@link TextReferences}): the stored text stays plain,
 * the reference sits in an annotation and is renamed together with the
 * element.
 */
public class TransitionContentEditor extends Composite {

	private enum TriggerKind {
		CHANGE, SIGNAL, CALL, TIME
	}

	private final EObject contextElement;

	private final List<TriggerRow> triggerRows = new ArrayList<>();

	private final Composite triggerListComposite;

	private final ReferenceText guardText;

	private final ReferenceText effectText;

	public TransitionContentEditor(Composite parent, int style, EObject contextElement) {
		super(parent, SWT.NONE);
		this.contextElement = contextElement;
		setLayout(new GridLayout(1, false));

		Label triggerLabel = new Label(this, SWT.NONE);
		triggerLabel.setText("Triggers:"); //$NON-NLS-1$

		triggerListComposite = new Composite(this, SWT.NONE);
		triggerListComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		triggerListComposite.setLayout(new GridLayout(5, false));

		Button addTriggerButton = new Button(this, SWT.PUSH);
		addTriggerButton.setText("Add Trigger"); //$NON-NLS-1$
		addTriggerButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			addTriggerRow(TriggerKind.CHANGE, false, "", null); //$NON-NLS-1$
			relayout();
		}));

		Label guardLabel = new Label(this, SWT.NONE);
		guardLabel.setText("Guard:"); //$NON-NLS-1$
		Composite guardComposite = new Composite(this, SWT.NONE);
		guardComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		guardComposite.setLayout(new GridLayout(2, false));
		guardText = new ReferenceText(guardComposite, SWT.MULTI | SWT.WRAP, contextElement);
		GridData guardData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		guardData.heightHint = 40;
		guardText.getControl().setLayoutData(guardData);
		Button guardRefButton = new Button(guardComposite, SWT.PUSH);
		guardRefButton.setText("Insert Reference..."); //$NON-NLS-1$
		guardRefButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> guardText.insertReference(getShell())));

		Label effectLabel = new Label(this, SWT.NONE);
		effectLabel.setText("Effect:"); //$NON-NLS-1$
		Composite effectComposite = new Composite(this, SWT.NONE);
		effectComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		effectComposite.setLayout(new GridLayout(2, false));
		effectText = new ReferenceText(effectComposite, SWT.MULTI | SWT.WRAP, contextElement);
		GridData effectData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		effectData.heightHint = 40;
		effectText.getControl().setLayoutData(effectData);
		Button effectRefButton = new Button(effectComposite, SWT.PUSH);
		effectRefButton.setText("Insert Reference..."); //$NON-NLS-1$
		effectRefButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> effectText.insertReference(getShell())));
	}

	/** Populates the editor's fields from the current state of {@code transition}. */
	public void load(Transition transition) {
		for (Trigger trigger : transition.getTriggers()) {
			Event event = trigger.getEvent();
			if (event instanceof ChangeEvent) {
				ValueSpecification expression = ((ChangeEvent) event).getChangeExpression();
				String changeText = StateMachineTextFormatter.textForValueSpecification(expression);
				addTriggerRow(TriggerKind.CHANGE, false, changeText, TextReferences.load(expression, changeText));
			} else if (event instanceof SignalEvent) {
				Signal signal = ((SignalEvent) event).getSignal();
				addTriggerRow(TriggerKind.SIGNAL, false, StateMachineTextFormatter.nameOrEmpty(signal), wholeTextReference(signal));
			} else if (event instanceof CallEvent) {
				Operation operation = ((CallEvent) event).getOperation();
				addTriggerRow(TriggerKind.CALL, false, StateMachineTextFormatter.nameOrEmpty(operation), wholeTextReference(operation));
			} else if (event instanceof TimeEvent) {
				TimeEvent timeEvent = (TimeEvent) event;
				TimeExpression when = timeEvent.getWhen();
				ValueSpecification expression = when == null ? null : when.getExpr();
				String value = StateMachineTextFormatter.textForValueSpecification(expression);
				addTriggerRow(TriggerKind.TIME, timeEvent.isRelative(), value, TextReferences.load(expression, value));
			}
		}
		Constraint guard = transition.getGuard();
		ValueSpecification guardSpecification = guard != null ? guard.getSpecification() : null;
		String guardValue = StateMachineTextFormatter.textForValueSpecification(guardSpecification);
		guardText.setContent(guardValue, TextReferences.load(guardSpecification, guardValue));
		String effectValue = StateMachineTextFormatter.textForBehavior(transition.getEffect());
		effectText.setContent(effectValue, TextReferences.load(transition.getEffect(), effectValue));
		relayout();
	}

	/**
	 * Reads the editor's current widget state into a plain data snapshot -
	 * call this from within {@link org.eclipse.jface.dialogs.Dialog#okPressed()}
	 * (i.e. while the widgets still exist), never after the dialog has
	 * closed. {@link #apply(Transition, Snapshot)} does the actual model
	 * mutation later, from the captured snapshot alone.
	 */
	public Snapshot capture() {
		List<TriggerSnapshot> triggers = new ArrayList<>();
		for (TriggerRow row : triggerRows) {
			triggers.add(new TriggerSnapshot(row.getKind(), row.relativeCheckbox.getSelection(), row.valueText.capture(true)));
		}
		return new Snapshot(triggers, guardText.capture(true), effectText.capture(false));
	}

	/**
	 * Applies a previously {@link #capture() captured} snapshot to
	 * {@code transition} - pure model mutation, safe to call after the
	 * dialog/editor that produced the snapshot has already been disposed.
	 */
	public static void apply(Transition transition, Snapshot snapshot) {
		Package container = transition.getNearestPackage();

		// The old triggers' events live in the package, not in the transition - they would stay behind
		// as unreferenced "<Change Event>" elements on every edit (37 of 103 in the real model).
		List<Event> oldEvents = eventsOf(transition);
		transition.getTriggers().clear();
		for (TriggerSnapshot row : snapshot.triggers) {
			Event event = createEvent(transition, row, container);
			if (event != null) {
				Trigger trigger = UMLFactory.eINSTANCE.createTrigger();
				trigger.setEvent(event);
				transition.getTriggers().add(trigger);
			}
		}
		deleteIfUnused(oldEvents, transition.eResource() == null ? null : transition.eResource().getResourceSet());

		if (snapshot.guard.text.isEmpty()) {
			transition.setGuard(null);
		} else {
			Constraint guard = transition.getGuard();
			if (guard == null) {
				guard = UMLFactory.eINSTANCE.createConstraint();
				container.getPackagedElements().add(guard);
				transition.setGuard(guard);
			}
			ValueSpecification specification = guard.getSpecification();
			if (specification instanceof LiteralString) {
				((LiteralString) specification).setValue(snapshot.guard.text);
			} else {
				LiteralString literal = UMLFactory.eINSTANCE.createLiteralString();
				literal.setValue(snapshot.guard.text);
				guard.setSpecification(literal);
				specification = literal;
			}
			TextReferences.store(specification, snapshot.guard.text, snapshot.guard.refs);
		}

		Behavior effect = transition.getEffect();
		if (effect instanceof OpaqueBehavior) {
			setBody((OpaqueBehavior) effect, snapshot.effect.text);
			TextReferences.store(effect, snapshot.effect.text, snapshot.effect.refs);
		} else if (!snapshot.effect.text.isEmpty()) {
			OpaqueBehavior behavior = UMLFactory.eINSTANCE.createOpaqueBehavior();
			container.getPackagedElements().add(behavior);
			setBody(behavior, snapshot.effect.text);
			transition.setEffect(behavior);
			TextReferences.store(behavior, snapshot.effect.text, snapshot.effect.refs);
		}
	}

	/** The events referenced by the triggers of {@code transition} (read before the triggers are changed or the transition is deleted). */
	static List<Event> eventsOf(Transition transition) {
		List<Event> events = new ArrayList<>();
		for (Trigger trigger : transition.getTriggers()) {
			if (trigger.getEvent() != null) {
				events.add(trigger.getEvent());
			}
		}
		return events;
	}

	/**
	 * Deletes each of {@code events} that nothing references any more - an
	 * event shared with another transition's trigger is left alone.
	 */
	static void deleteIfUnused(List<Event> events, ResourceSet resourceSet) {
		if (resourceSet == null) {
			return;
		}
		for (Event event : events) {
			if (event.eContainer() != null && EcoreUtil.UsageCrossReferencer.find(event, resourceSet).isEmpty()) {
				EcoreUtil.delete(event);
			}
		}
	}

	/** Every notation {@link View} in the loaded {@code .notation} resources that matches {@code filter}. */
	static List<View> findViews(ResourceSet resourceSet, Predicate<View> filter) {
		List<View> result = new ArrayList<>();
		if (resourceSet == null) {
			return result;
		}
		for (Resource resource : resourceSet.getResources()) {
			String extension = resource.getURI() == null ? null : resource.getURI().fileExtension();
			if (!"notation".equals(extension)) { //$NON-NLS-1$
				continue;
			}
			java.util.Iterator<EObject> it = resource.getAllContents();
			while (it.hasNext()) {
				EObject candidate = it.next();
				if (candidate instanceof View && filter.test((View) candidate)) {
					result.add((View) candidate);
				}
			}
		}
		return result;
	}

	private static Event createEvent(Transition transition, TriggerSnapshot row, Package container) {
		switch (row.kind) {
		case SIGNAL:
			Signal signal = fromReferenceOrName(transition, Signal.class, row.value);
			if (signal == null) {
				return null;
			}
			SignalEvent signalEvent = UMLFactory.eINSTANCE.createSignalEvent();
			signalEvent.setSignal(signal);
			container.getPackagedElements().add(signalEvent);
			return signalEvent;
		case CALL:
			Operation operation = fromReferenceOrName(transition, Operation.class, row.value);
			if (operation == null) {
				return null;
			}
			CallEvent callEvent = UMLFactory.eINSTANCE.createCallEvent();
			callEvent.setOperation(operation);
			container.getPackagedElements().add(callEvent);
			return callEvent;
		case TIME:
			TimeEvent timeEvent = UMLFactory.eINSTANCE.createTimeEvent();
			timeEvent.setIsRelative(row.relative);
			TimeExpression timeExpression = UMLFactory.eINSTANCE.createTimeExpression();
			LiteralString timeLiteral = UMLFactory.eINSTANCE.createLiteralString();
			timeLiteral.setValue(row.value.text);
			TextReferences.store(timeLiteral, row.value.text, row.value.refs);
			timeExpression.setExpr(timeLiteral);
			timeEvent.setWhen(timeExpression);
			container.getPackagedElements().add(timeEvent);
			return timeEvent;
		case CHANGE:
		default:
			ChangeEvent changeEvent = UMLFactory.eINSTANCE.createChangeEvent();
			LiteralString changeLiteral = UMLFactory.eINSTANCE.createLiteralString();
			changeLiteral.setValue(row.value.text);
			TextReferences.store(changeLiteral, row.value.text, row.value.refs);
			changeEvent.setChangeExpression(changeLiteral);
			container.getPackagedElements().add(changeEvent);
			return changeEvent;
		}
	}

	private static void setBody(OpaqueBehavior behavior, String body) {
		if (behavior.getBodies().isEmpty()) {
			behavior.getBodies().add(body);
			behavior.getLanguages().add("Natural language"); //$NON-NLS-1$
		} else {
			behavior.getBodies().set(0, body);
		}
	}

	/** A reference covering the whole field text, pointing at {@code target} (shown for Signal/Call triggers, which really link the element). */
	private static List<Ref> wholeTextReference(NamedElement target) {
		List<Ref> refs = new ArrayList<>();
		if (target != null && target.getName() != null && !target.getName().isEmpty()) {
			refs.add(new Ref(0, target.getName().length(), EcoreUtil.getURI(target).toString()));
		}
		return refs;
	}

	/**
	 * The element a Signal/Call trigger row stands for: the referenced element if
	 * the field still is exactly one intact reference (exact, also for equally
	 * named elements), otherwise the first element found by name.
	 */
	private static <T extends NamedElement> T fromReferenceOrName(Transition transition, java.lang.Class<T> type, Value value) {
		if (value.refs.size() == 1 && value.refs.get(0).start == 0 && value.refs.get(0).length == value.text.length()
				&& transition.eResource() != null) {
			NamedElement target = TextReferences.resolve(value.refs.get(0).uri, transition.eResource().getResourceSet());
			if (type.isInstance(target)) {
				return type.cast(target);
			}
		}
		return findByName(transition, type, value.text);
	}

	private static <T extends NamedElement> T findByName(Transition transition, java.lang.Class<T> type, String name) {
		if (name == null || name.isEmpty() || transition.eResource() == null) {
			return null;
		}
		ResourceSet resourceSet = transition.eResource().getResourceSet();
		if (resourceSet == null) {
			return null;
		}
		for (Resource resource : resourceSet.getResources()) {
			java.util.Iterator<EObject> it = resource.getAllContents();
			while (it.hasNext()) {
				EObject candidate = it.next();
				if (type.isInstance(candidate) && name.equals(((NamedElement) candidate).getName())) {
					return type.cast(candidate);
				}
			}
		}
		return null;
	}

	/** Plain-data snapshot of one trigger row, independent of its (possibly already-disposed) widgets. */
	public static final class TriggerSnapshot {

		final TriggerKind kind;

		final boolean relative;

		final Value value;

		TriggerSnapshot(TriggerKind kind, boolean relative, Value value) {
			this.kind = kind;
			this.relative = relative;
			this.value = value;
		}
	}

	/** Plain-data snapshot of the whole editor, independent of its (possibly already-disposed) widgets. */
	public static final class Snapshot {

		final List<TriggerSnapshot> triggers;

		final Value guard;

		final Value effect;

		Snapshot(List<TriggerSnapshot> triggers, Value guard, Value effect) {
			this.triggers = triggers;
			this.guard = guard;
			this.effect = effect;
		}
	}

	private void addTriggerRow(TriggerKind kind, boolean relative, String value, List<Ref> refs) {
		TriggerRow row = new TriggerRow(kind, relative, value, refs);
		triggerRows.add(row);
	}

	private void removeTriggerRow(TriggerRow row) {
		triggerRows.remove(row);
		for (Control control : row.controls) {
			control.dispose();
		}
		relayout();
	}

	private void relayout() {
		triggerListComposite.layout(true, true);
		layout(true, true);
		if (getParent() != null) {
			getParent().layout(true, true);
		}
	}

	private final class TriggerRow {

		final List<Control> controls = new ArrayList<>();

		final Combo kindCombo;

		final Button relativeCheckbox;

		final ReferenceText valueText;

		TriggerRow(TriggerKind kind, boolean relative, String value, List<Ref> refs) {
			kindCombo = new Combo(triggerListComposite, SWT.READ_ONLY);
			kindCombo.setItems("Change", "Signal", "Call", "Time"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			kindCombo.select(kind.ordinal());
			controls.add(kindCombo);

			relativeCheckbox = new Button(triggerListComposite, SWT.CHECK);
			relativeCheckbox.setText("relative"); //$NON-NLS-1$
			relativeCheckbox.setToolTipText("Only used for Time triggers: relative (\"after\") vs. absolute (\"at\")."); //$NON-NLS-1$
			relativeCheckbox.setSelection(relative);
			controls.add(relativeCheckbox);

			valueText = new ReferenceText(triggerListComposite, SWT.SINGLE, contextElement);
			valueText.setContent(value, refs);
			valueText.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			controls.add(valueText.getControl());

			Button insertRefButton = new Button(triggerListComposite, SWT.PUSH);
			insertRefButton.setText("Insert Reference..."); //$NON-NLS-1$
			insertRefButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> valueText.insertReference(getShell())));
			controls.add(insertRefButton);

			Button removeButton = new Button(triggerListComposite, SWT.PUSH);
			removeButton.setText("Remove"); //$NON-NLS-1$
			removeButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> removeTriggerRow(this)));
			controls.add(removeButton);
		}

		TriggerKind getKind() {
			return TriggerKind.values()[kindCombo.getSelectionIndex()];
		}
	}
}
