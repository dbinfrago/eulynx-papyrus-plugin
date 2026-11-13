// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.operation;

import java.util.ArrayList;
import java.util.List;

import com.eulynx.papyrus.specification.richtext.TextReferences;
import com.eulynx.papyrus.specification.richtext.TextReferences.Value;
import com.eulynx.papyrus.specification.statemachine.ui.ReferenceText;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.UMLFactory;

/**
 * "Edit Operation Body..." dialog: edits the body of an operation's method(s)
 * with "Insert Reference..." (live references, see {@link TextReferences}), so
 * a rename of an attribute, signal or literal reaches the operation text as
 * well. An operation without a method gets a new one on OK if text was entered.
 */
public class EditOperationDialog extends TitleAreaDialog {

	private static final class MethodRow {

		final OpaqueBehavior behavior;

		ReferenceText text;

		Value value;

		MethodRow(OpaqueBehavior behavior) {
			this.behavior = behavior;
		}
	}

	private final Operation operation;

	private final List<MethodRow> rows = new ArrayList<>();

	public EditOperationDialog(Shell parentShell, Operation operation) {
		super(parentShell);
		this.operation = operation;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit Operation Body"); //$NON-NLS-1$
		setMessage("Edit the body of \"" + (operation.getName() == null ? "" : operation.getName()) + "\"."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Composite area = (Composite) super.createDialogArea(parent);
		area.setLayout(new GridLayout(1, false));

		List<OpaqueBehavior> methods = new ArrayList<>();
		for (Behavior method : operation.getMethods()) {
			if (method instanceof OpaqueBehavior) {
				methods.add((OpaqueBehavior) method);
			}
		}
		if (methods.isEmpty()) {
			createRow(area, null, "Body:"); //$NON-NLS-1$
		} else {
			for (OpaqueBehavior method : methods) {
				String name = method.getName() == null || method.getName().isEmpty() ? "Body:" : method.getName() + ":"; //$NON-NLS-1$ //$NON-NLS-2$
				createRow(area, method, name);
			}
		}
		return area;
	}

	private void createRow(Composite parent, OpaqueBehavior behavior, String labelText) {
		MethodRow row = new MethodRow(behavior);
		rows.add(row);
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Composite fieldComposite = new Composite(parent, SWT.NONE);
		fieldComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		fieldComposite.setLayout(new GridLayout(2, false));
		row.text = new ReferenceText(fieldComposite, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL, operation);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.widthHint = 640;
		data.heightHint = 320;
		row.text.getControl().setLayoutData(data);
		Button insertRefButton = new Button(fieldComposite, SWT.PUSH);
		insertRefButton.setText("Insert Reference..."); //$NON-NLS-1$
		insertRefButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		insertRefButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> row.text.insertReference(getShell())));
		if (behavior != null) {
			String body = behavior.getBodies().isEmpty() ? "" : behavior.getBodies().get(0); //$NON-NLS-1$
			row.text.setContent(body, TextReferences.load(behavior, body));
		}
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit Operation Body"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		// Capture widget state now: open() only returns (and applyChanges() only runs)
		// after the Shell and all its widgets have been disposed.
		for (MethodRow row : rows) {
			row.value = row.text.capture(false);
		}
		super.okPressed();
	}

	/** Writes the captured texts onto the operation's method(s) - call inside a {@code RecordingCommand} after {@code open() == Window.OK}. */
	public void applyChanges() {
		for (MethodRow row : rows) {
			if (row.value == null) {
				continue;
			}
			OpaqueBehavior behavior = row.behavior;
			if (behavior == null) {
				if (row.value.text.isEmpty() || operation.getClass_() == null) {
					continue;
				}
				behavior = UMLFactory.eINSTANCE.createOpaqueBehavior();
				behavior.setName(operation.getName());
				operation.getClass_().getOwnedBehaviors().add(behavior);
				behavior.setSpecification(operation);
			}
			if (behavior.getBodies().isEmpty()) {
				behavior.getBodies().add(row.value.text);
				behavior.getLanguages().add("Natural language"); //$NON-NLS-1$
			} else {
				behavior.getBodies().set(0, row.value.text);
			}
			TextReferences.store(behavior, row.value.text, row.value.refs);
		}
	}
}
