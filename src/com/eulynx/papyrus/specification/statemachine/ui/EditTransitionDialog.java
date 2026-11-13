package com.eulynx.papyrus.specification.statemachine.ui;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Transition;

/**
 * "Edit Transition..." dialog: trigger list + guard + effect for one
 * (external) {@link Transition}, via {@link TransitionContentEditor}.
 */
public class EditTransitionDialog extends TitleAreaDialog {

	private final Transition transition;

	private TransitionContentEditor editor;

	private TransitionContentEditor.Snapshot snapshot;

	public EditTransitionDialog(Shell parentShell, Transition transition) {
		super(parentShell);
		this.transition = transition;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit Transition"); //$NON-NLS-1$
		setMessage("Edit the trigger(s), guard, and effect of this transition."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		editor = new TransitionContentEditor(composite, SWT.NONE, transition);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.widthHint = 500;
		editor.setLayoutData(data);
		editor.load(transition);

		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit Transition"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		// Capture widget state now, while the widgets still exist - dialog.open()
		// only returns to the caller (and applyChanges() only runs) after this
		// dialog's Shell (and every widget in it) has already been disposed.
		snapshot = editor.capture();
		super.okPressed();
	}

	/**
	 * Writes the captured snapshot back onto the transition - call this from
	 * within a {@code RecordingCommand} after {@code open() == Window.OK},
	 * never directly from {@link #okPressed()}, since that runs outside any
	 * editing-domain transaction.
	 */
	public void applyChanges() {
		TransitionContentEditor.apply(transition, snapshot);
	}
}
