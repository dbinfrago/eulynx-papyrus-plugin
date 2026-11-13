// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.timing;

import java.util.List;

import com.eulynx.papyrus.specification.richtext.HtmlStyledTextEditor;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Dialog for a duration between two events ({@link DurationLinkSupport}): an operator (list) and an
 * expression with "Insert Reference..." to pick model elements (e.g. the
 * Blocks that define a time value). The diagram then shows
 * {@code {<operator> <expression>}}.
 *
 * <p>
 * Like every dialog in this plugin, the result is copied into plain fields in
 * {@link #okPressed()}: the widgets are already disposed when {@code open()}
 * returns.
 */
public class DurationArrowDialog extends TitleAreaDialog {

	/** Shown for the empty operator. */
	private static final String NO_OPERATOR = "(none)"; //$NON-NLS-1$

	private final EObject contextElement;

	private final String initialOperator;

	private final String initialExpressionHtml;

	/** Labels of the selectable events; null when only operator/expression are edited (existing duration). */
	private final List<String> endpointLabels;

	private final int initialStartIndex;

	private final int initialEndIndex;

	private Combo startCombo;

	private Combo endCombo;

	private Combo operatorCombo;

	private HtmlStyledTextEditor expressionEditor;

	private int startIndex = -1;

	private int endIndex = -1;

	private String operator = ""; //$NON-NLS-1$

	private String expressionHtml = ""; //$NON-NLS-1$

	public DurationArrowDialog(Shell parentShell, EObject contextElement, String initialOperator, String initialExpressionHtml) {
		this(parentShell, contextElement, initialOperator, initialExpressionHtml, null, -1, -1);
	}

	/** With start/end event selection (a duration between two events of the diagram). */
	public DurationArrowDialog(Shell parentShell, EObject contextElement, String initialOperator, String initialExpressionHtml,
			List<String> endpointLabels, int initialStartIndex, int initialEndIndex) {
		super(parentShell);
		this.contextElement = contextElement;
		this.initialOperator = initialOperator == null ? "" : initialOperator; //$NON-NLS-1$
		this.initialExpressionHtml = initialExpressionHtml == null ? "" : initialExpressionHtml; //$NON-NLS-1$
		this.endpointLabels = endpointLabels;
		this.initialStartIndex = initialStartIndex;
		this.initialEndIndex = initialEndIndex;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Duration Arrow"); //$NON-NLS-1$
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Duration Arrow"); //$NON-NLS-1$
		setMessage("Shown next to the arrow as {operator expression}."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);
		Composite area = new Composite(composite, SWT.NONE);
		area.setLayout(new GridLayout(2, false));
		area.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		if (endpointLabels != null) {
			startCombo = createEndpointCombo(area, "Start event:", initialStartIndex); //$NON-NLS-1$
			endCombo = createEndpointCombo(area, "End event:", initialEndIndex); //$NON-NLS-1$
		}

		new Label(area, SWT.NONE).setText("Operator:"); //$NON-NLS-1$
		operatorCombo = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
		int selected = 0;
		for (int i = 0; i < DurationArrowSupport.OPERATORS.length; i++) {
			String op = DurationArrowSupport.OPERATORS[i];
			operatorCombo.add(op.isEmpty() ? NO_OPERATOR : op);
			if (op.equals(initialOperator)) {
				selected = i;
			}
		}
		operatorCombo.select(selected);

		Label expressionLabel = new Label(area, SWT.NONE);
		expressionLabel.setText("Expression:"); //$NON-NLS-1$
		expressionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		expressionEditor = new HtmlStyledTextEditor(area, SWT.NONE, contextElement);
		GridData editorData = new GridData(SWT.FILL, SWT.FILL, true, true);
		editorData.widthHint = 420;
		editorData.heightHint = 90;
		expressionEditor.setLayoutData(editorData);
		expressionEditor.setHtml(initialExpressionHtml);

		return composite;
	}

	private Combo createEndpointCombo(Composite area, String caption, int selected) {
		new Label(area, SWT.NONE).setText(caption);
		Combo combo = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		for (String endpointLabel : endpointLabels) {
			combo.add(endpointLabel);
		}
		if (selected >= 0 && selected < endpointLabels.size()) {
			combo.select(selected);
		}
		return combo;
	}

	@Override
	protected void okPressed() {
		if (endpointLabels != null) {
			int start = startCombo.getSelectionIndex();
			int end = endCombo.getSelectionIndex();
			if (start < 0 || end < 0) {
				setErrorMessage("Please select a start and an end event."); //$NON-NLS-1$
				return;
			}
			if (start == end) {
				setErrorMessage("Start and end event must be different."); //$NON-NLS-1$
				return;
			}
			startIndex = start;
			endIndex = end;
		}
		int index = operatorCombo.getSelectionIndex();
		operator = index < 0 ? "" : DurationArrowSupport.OPERATORS[index]; //$NON-NLS-1$
		expressionHtml = expressionEditor.getHtml();
		super.okPressed();
	}

	public int getStartIndex() {
		return startIndex;
	}

	public int getEndIndex() {
		return endIndex;
	}

	public String getOperator() {
		return operator;
	}

	public String getExpressionHtml() {
		return expressionHtml;
	}
}
