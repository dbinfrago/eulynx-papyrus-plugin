// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.comment;

import com.eulynx.papyrus.specification.richtext.HtmlStyledTextEditor;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Comment;

/**
 * Small dialog to edit a UML {@link Comment}'s {@code body} with the same
 * Bold/Italic/Bullet-list/Insert-Reference editor used for Requirement text -
 * useful e.g. for Comments placed on Sequence Diagrams.
 */
public class EditCommentDialog extends TitleAreaDialog {

	private final Comment comment;

	private HtmlStyledTextEditor bodyText;

	private String commentBody;

	public EditCommentDialog(Shell parentShell, Comment comment) {
		super(parentShell);
		this.comment = comment;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit Comment"); //$NON-NLS-1$
		setMessage("Edit the text of this comment."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		bodyText = new HtmlStyledTextEditor(composite, SWT.NONE, comment);
		GridData bodyData = new GridData(SWT.FILL, SWT.FILL, true, true);
		bodyData.heightHint = 150;
		bodyData.widthHint = 400;
		bodyText.setLayoutData(bodyData);
		String currentBody = comment.getBody();
		bodyText.setHtml(currentBody == null ? "" : currentBody); //$NON-NLS-1$

		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit Comment"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		commentBody = bodyText.getHtml();
		super.okPressed();
	}

	public String getCommentBody() {
		return commentBody;
	}
}
