// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.requirement;

import com.eulynx.papyrus.specification.richtext.HtmlStyledTextEditor;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Stereotype;

/**
 * Small dialog to edit a {@code «Requirement»}'s {@code id} and {@code text}
 * stereotype properties directly, instead of the generic Profile-tab
 * stereotype-property tree in the Properties view.
 */
public class EditRequirementDialog extends TitleAreaDialog {

	private final Element requirement;

	private final Stereotype stereotype;

	private Text idText;

	private HtmlStyledTextEditor bodyText;

	private String requirementId;

	private String requirementText;

	public EditRequirementDialog(Shell parentShell, Element requirement, Stereotype stereotype) {
		super(parentShell);
		this.requirement = requirement;
		this.stereotype = stereotype;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit Requirement"); //$NON-NLS-1$
		String elementName = requirement instanceof NamedElement ? ((NamedElement) requirement).getQualifiedName() : requirement.toString();
		setMessage("Edit the id and text of \"" + elementName + "\"."); //$NON-NLS-1$ //$NON-NLS-2$
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(2, false));

		Label idLabel = new Label(composite, SWT.NONE);
		idLabel.setText("Id:"); //$NON-NLS-1$
		idText = new Text(composite, SWT.BORDER);
		idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Object currentId = requirement.getValue(stereotype, "id"); //$NON-NLS-1$
		idText.setText(currentId == null ? "" : currentId.toString()); //$NON-NLS-1$

		Label textLabel = new Label(composite, SWT.NONE);
		textLabel.setText("Text:"); //$NON-NLS-1$
		textLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		bodyText = new HtmlStyledTextEditor(composite, SWT.NONE, requirement);
		GridData bodyData = new GridData(SWT.FILL, SWT.FILL, true, true);
		bodyData.heightHint = 150;
		bodyData.widthHint = 400;
		bodyText.setLayoutData(bodyData);
		Object currentText = requirement.getValue(stereotype, "text"); //$NON-NLS-1$
		bodyText.setHtml(currentText == null ? "" : currentText.toString()); //$NON-NLS-1$

		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit Requirement"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		requirementId = idText.getText();
		requirementText = bodyText.getHtml();
		super.okPressed();
	}

	public String getRequirementId() {
		return requirementId;
	}

	public String getRequirementText() {
		return requirementText;
	}
}
