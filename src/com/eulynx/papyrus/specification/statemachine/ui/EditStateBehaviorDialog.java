package com.eulynx.papyrus.specification.statemachine.ui;

import java.util.ArrayList;
import java.util.List;

import com.eulynx.papyrus.specification.richtext.TextReferences;
import com.eulynx.papyrus.specification.richtext.TextReferences.Value;
import com.eulynx.papyrus.specification.statemachine.StateMachineTextFormatter;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gmf.runtime.diagram.core.util.ViewUtil;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.Event;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.State;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.TransitionKind;
import org.eclipse.uml2.uml.UMLFactory;

/**
 * "Edit State Behavior..." dialog: entry/exit/doActivity bodies plus a
 * dynamic list of the state's internal (change/signal/call/time-triggered)
 * transitions, each edited via an embedded {@link TransitionContentEditor} -
 * same reuse-or-create logic as {@link EditTransitionDialog}, since internal
 * transitions are plain {@link Transition}s with {@code kind ==
 * TransitionKind#INTERNAL_LITERAL} and {@code source == target == state}.
 */
public class EditStateBehaviorDialog extends TitleAreaDialog {

	private final State state;

	private final List<Transition> removedTransitions = new ArrayList<>();

	private final List<InternalTransitionRow> rows = new ArrayList<>();

	private ReferenceText entryText;

	private ReferenceText exitText;

	private ReferenceText doText;

	private Composite internalTransitionsComposite;

	private ScrolledComposite scrolled;

	/** The scrolled content: entry/exit/doActivity fields plus the internal-transition list. */
	private Composite content;

	private Value entryValue;

	private Value exitValue;

	private Value doValue;

	public EditStateBehaviorDialog(Shell parentShell, State state) {
		super(parentShell);
		this.state = state;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit State Behavior"); //$NON-NLS-1$
		setMessage("Edit \"" + StateMachineTextFormatter.nameOrEmpty(state) + "\"'s entry/exit/doActivity and internal transitions."); //$NON-NLS-1$ //$NON-NLS-2$
		Composite area = (Composite) super.createDialogArea(parent);
		area.setLayout(new GridLayout(1, false));

		// With many internal transitions the content grows far beyond the screen - it lives in a
		// scrollable viewport with a fixed preferred size instead of growing the dialog endlessly.
		scrolled = new ScrolledComposite(area, SWT.V_SCROLL | SWT.H_SCROLL);
		GridData scrolledData = new GridData(SWT.FILL, SWT.FILL, true, true);
		scrolledData.widthHint = 760;
		scrolledData.heightHint = 560;
		scrolled.setLayoutData(scrolledData);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		Composite composite = new Composite(scrolled, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		scrolled.setContent(composite);
		content = composite;

		entryText = createBehaviorField(composite, "entry:"); //$NON-NLS-1$
		exitText = createBehaviorField(composite, "exit:"); //$NON-NLS-1$
		doText = createBehaviorField(composite, "doActivity:"); //$NON-NLS-1$

		Label internalLabel = new Label(composite, SWT.NONE);
		internalLabel.setText("Internal transitions:"); //$NON-NLS-1$

		internalTransitionsComposite = new Composite(composite, SWT.NONE);
		internalTransitionsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		internalTransitionsComposite.setLayout(new GridLayout(1, false));

		Button addButton = new Button(composite, SWT.PUSH);
		addButton.setText("Add Internal Transition"); //$NON-NLS-1$
		addButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			addRow(null);
			relayout();
			// bring the new row into view - it is appended at the end of a possibly long list
			if (!rows.isEmpty()) {
				scrolled.showControl(rows.get(rows.size() - 1).rowContainer);
			}
		}));

		showBehavior(entryText, state.getEntry());
		showBehavior(exitText, state.getExit());
		showBehavior(doText, state.getDoActivity());
		for (Transition transition : state.getContainer().getTransitions()) {
			if (transition.getKind() == TransitionKind.INTERNAL_LITERAL && transition.getSource() == state) {
				addRow(transition);
			}
		}
		relayout();

		return area;
	}

	private static void showBehavior(ReferenceText field, Behavior behavior) {
		String text = StateMachineTextFormatter.textForBehavior(behavior);
		field.setContent(text, TextReferences.load(behavior, text));
	}

	private ReferenceText createBehaviorField(Composite parent, String labelText) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Composite fieldComposite = new Composite(parent, SWT.NONE);
		fieldComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fieldComposite.setLayout(new GridLayout(2, false));
		ReferenceText text = new ReferenceText(fieldComposite, SWT.MULTI | SWT.WRAP, state);
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.heightHint = 40;
		text.getControl().setLayoutData(data);
		Button insertRefButton = new Button(fieldComposite, SWT.PUSH);
		insertRefButton.setText("Insert Reference..."); //$NON-NLS-1$
		insertRefButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> text.insertReference(getShell())));
		return text;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit State Behavior"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		// Capture widget state now, while the widgets still exist - dialog.open()
		// only returns to the caller (and applyChanges() only runs) after this
		// dialog's Shell (and every widget in it) has already been disposed.
		entryValue = entryText.capture(false);
		exitValue = exitText.capture(false);
		doValue = doText.capture(false);
		for (InternalTransitionRow row : rows) {
			row.snapshot = row.editor.capture();
		}
		super.okPressed();
	}

	/**
	 * Writes the captured field values back onto the state - call this from
	 * within a {@code RecordingCommand} after {@code open() == Window.OK},
	 * never directly from {@link #okPressed()}, since that runs outside any
	 * editing-domain transaction.
	 */
	public void applyChanges() {
		state.setEntry(reuseOrCreateBehavior(state.getEntry(), entryValue));
		state.setExit(reuseOrCreateBehavior(state.getExit(), exitValue));
		state.setDoActivity(reuseOrCreateBehavior(state.getDoActivity(), doValue));

		ResourceSet resourceSet = state.eResource() == null ? null : state.eResource().getResourceSet();
		for (Transition removed : removedTransitions) {
			deleteInternalTransition(removed, resourceSet);
		}
		// leftovers of earlier removals: lines in the state's compartment whose
		// transition is already gone and that can't be deleted from the diagram by hand
		removeStaleInternalTransitionViews(resourceSet);

		for (InternalTransitionRow row : rows) {
			Transition transition = row.transition;
			if (transition == null) {
				transition = UMLFactory.eINSTANCE.createTransition();
				transition.setKind(TransitionKind.INTERNAL_LITERAL);
				transition.setSource(state);
				transition.setTarget(state);
				state.getContainer().getTransitions().add(transition);
			}
			TransitionContentEditor.apply(transition, row.snapshot);
		}
	}

	/** Notation type of one internal-transition line inside a state's compartment (Papyrus' {@code InternalTransitionEditPart.VISUAL_ID}). */
	private static final String INTERNAL_TRANSITION_VIEW_TYPE = "Transition_InternalTransitionLabel"; //$NON-NLS-1$

	/**
	 * Deletes an internal transition <b>including its diagram lines</b>.
	 * {@code EcoreUtil.delete} alone only removes the model element and
	 * resets the {@code element} reference of every view pointing at it to
	 * {@code null} - the line stays in the state's compartment as a ghost
	 * (confirmed in the real {@code .notation}: views with an empty
	 * {@code element}) which can't be deleted from the diagram any more. The
	 * views therefore go first; the now unreferenced events of its triggers
	 * are cleaned up afterwards.
	 */
	private static void deleteInternalTransition(Transition removed, ResourceSet resourceSet) {
		List<Event> events = TransitionContentEditor.eventsOf(removed);
		for (View view : TransitionContentEditor.findViews(resourceSet, v -> v.getElement() == removed)) {
			ViewUtil.destroy(view);
		}
		EcoreUtil.delete(removed);
		TransitionContentEditor.deleteIfUnused(events, resourceSet);
	}

	/** Destroys internal-transition lines of {@link #state}'s diagram shapes that no longer have a model element. */
	private void removeStaleInternalTransitionViews(ResourceSet resourceSet) {
		List<View> stale = new ArrayList<>();
		for (View stateView : TransitionContentEditor.findViews(resourceSet, v -> v.getElement() == state)) {
			TreeIterator<EObject> it = stateView.eAllContents();
			while (it.hasNext()) {
				EObject candidate = it.next();
				if (candidate instanceof View) {
					View view = (View) candidate;
					if (INTERNAL_TRANSITION_VIEW_TYPE.equals(view.getType())
							&& (view.getElement() == null || view.getElement().eIsProxy())) {
						stale.add(view);
					}
				}
			}
		}
		for (View view : stale) {
			ViewUtil.destroy(view);
		}
	}

	private static Behavior reuseOrCreateBehavior(Behavior existing, Value value) {
		if (value == null || value.text.isEmpty()) {
			return null;
		}
		if (existing instanceof OpaqueBehavior) {
			setBody((OpaqueBehavior) existing, value.text);
			TextReferences.store(existing, value.text, value.refs);
			return existing;
		}
		OpaqueBehavior behavior = UMLFactory.eINSTANCE.createOpaqueBehavior();
		setBody(behavior, value.text);
		TextReferences.store(behavior, value.text, value.refs);
		return behavior;
	}

	private static void setBody(OpaqueBehavior behavior, String body) {
		if (behavior.getBodies().isEmpty()) {
			behavior.getBodies().add(body);
			behavior.getLanguages().add("Natural language"); //$NON-NLS-1$
		} else {
			behavior.getBodies().set(0, body);
		}
	}

	private void addRow(Transition existing) {
		InternalTransitionRow row = new InternalTransitionRow(existing);
		rows.add(row);
	}

	private void removeRow(InternalTransitionRow row) {
		rows.remove(row);
		if (row.transition != null) {
			removedTransitions.add(row.transition);
		}
		row.rowContainer.dispose();
		relayout();
	}

	private void relayout() {
		internalTransitionsComposite.layout(true, true);
		if (internalTransitionsComposite.getParent() != null) {
			internalTransitionsComposite.getParent().layout(true, true);
		}
		// the scrolled viewport has to learn the new content size, otherwise the scroll bar is wrong
		if (scrolled != null && content != null && !scrolled.isDisposed()) {
			scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT, true));
		}
	}

	private final class InternalTransitionRow {

		final Transition transition;

		final Composite rowContainer;

		final TransitionContentEditor editor;

		TransitionContentEditor.Snapshot snapshot;

		InternalTransitionRow(Transition transition) {
			this.transition = transition;

			rowContainer = new Composite(internalTransitionsComposite, SWT.BORDER);
			rowContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			rowContainer.setLayout(new GridLayout(1, false));

			editor = new TransitionContentEditor(rowContainer, SWT.NONE, state);
			editor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			if (transition != null) {
				editor.load(transition);
			}

			Button removeButton = new Button(rowContainer, SWT.PUSH);
			removeButton.setText("Remove Internal Transition"); //$NON-NLS-1$
			removeButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> removeRow(this)));
		}
	}
}
