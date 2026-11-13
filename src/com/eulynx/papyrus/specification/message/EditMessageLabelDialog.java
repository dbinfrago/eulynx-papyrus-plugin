package com.eulynx.papyrus.specification.message;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Message;

import com.eulynx.papyrus.specification.richtext.HtmlStyledTextEditor;

/** Dialog für den Label-Text einer Message, mit "Insert Reference...". */
public class EditMessageLabelDialog extends TitleAreaDialog {

	private final Message message;

	private HtmlStyledTextEditor editor;

	private String html;

	public EditMessageLabelDialog(Shell parentShell, Message message) {
		super(parentShell);
		this.message = message;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Edit Message Label"); //$NON-NLS-1$
		setMessage("Text der Message. Mit \"Insert Reference...\" Signal, Attribut oder Enumeration-Literal einfügen."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		editor = new HtmlStyledTextEditor(composite, SWT.NONE, message);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 80;
		data.widthHint = 450;
		editor.setLayoutData(data);
		editor.setHtml(initialHtml());
		return composite;
	}

	/**
	 * Gespeicherter Text; wurde der Name seitdem direkt im Diagramm geändert,
	 * gilt der Name.
	 */
	private String initialHtml() {
		String stored = MessageLabelSupport.getHtml(message);
		String name = message.getName() == null ? "" : message.getName(); //$NON-NLS-1$
		if (stored != null && MessageLabelSupport.plainText(stored).equals(name)) {
			return stored;
		}
		return name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Edit Message Label"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		// Widget-Zustand vor dem Schließen übernehmen (Dialog wird danach disposed).
		html = editor.getHtml();
		super.okPressed();
	}

	public String getHtml() {
		return html;
	}
}
