package com.eulynx.papyrus.specification.richtext;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gef.EditPart;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.part.ViewPart;

/**
 * Editable preview of the currently selected element's rich text - the same
 * {@link HtmlStyledTextEditor} used by the "Edit Requirement..."/"Edit
 * Comment..." dialogs, but always docked and live-updating with the current
 * selection, so formatting/references are visible (and editable) without opening
 * a separate dialog. Handles two kinds of source: a {@code
 * «Requirement»}-stereotyped element's {@code text} stereotype property, or a
 * plain UML {@link Comment}'s {@code body} (used e.g. on Sequence Diagrams). A
 * standalone view rather than a Properties-view tab, since the Properties-view
 * "Context" framework turned out to be non-functional in this Papyrus install.
 * <p>
 * Edits are explicit: switching the selection to a different element
 * discards any unsaved change in this view (use the Save button first) - the
 * same as closing an "Edit ..." dialog without pressing OK.
 */
public class RichTextPreviewView extends ViewPart {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	private HtmlStyledTextEditor bodyEditor;

	private Element currentRequirement;

	private Stereotype currentStereotype;

	private Comment currentComment;

	private final ISelectionListener selectionListener = (part, selection) -> updateFromSelection(selection);

	@Override
	public void createPartControl(Composite parent) {
		bodyEditor = new HtmlStyledTextEditor(parent, SWT.NONE, null);
		bodyEditor.setEditable(false);

		Button saveButton = new Button(bodyEditor.getToolbar(), SWT.PUSH);
		saveButton.setText("Save"); //$NON-NLS-1$
		saveButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> save()));
		bodyEditor.getToolbar().layout();

		getSite().getPage().addPostSelectionListener(selectionListener);
	}

	@Override
	public void setFocus() {
		bodyEditor.setFocus();
	}

	@Override
	public void dispose() {
		getSite().getPage().removePostSelectionListener(selectionListener);
		super.dispose();
	}

	private void save() {
		String html = bodyEditor.getHtml();
		if (currentComment != null) {
			Comment comment = currentComment;
			TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(comment);
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Comment") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					comment.setBody(html);
				}
			});
		} else if (currentRequirement != null && currentStereotype != null) {
			Element requirement = currentRequirement;
			Stereotype stereotype = currentStereotype;
			TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(requirement);
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Requirement Text") { //$NON-NLS-1$
				@Override
				protected void doExecute() {
					requirement.setValue(stereotype, "text", html); //$NON-NLS-1$
				}
			});
		}
	}

	private void updateFromSelection(ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}
		Element element = toElement(((IStructuredSelection) selection).getFirstElement());
		currentRequirement = null;
		currentStereotype = null;
		currentComment = null;

		if (element instanceof Comment) {
			currentComment = (Comment) element;
			bodyEditor.setContextElement(currentComment);
			bodyEditor.setEditable(true);
			bodyEditor.setHtml(currentComment.getBody() == null ? "" : currentComment.getBody()); //$NON-NLS-1$
			return;
		}

		Stereotype stereotype = element == null ? null : element.getAppliedStereotype(REQUIREMENT_STEREOTYPE);
		if (stereotype != null) {
			currentRequirement = element;
			currentStereotype = stereotype;
			bodyEditor.setContextElement(currentRequirement);
			bodyEditor.setEditable(true);
			Object value = currentRequirement.getValue(currentStereotype, "text"); //$NON-NLS-1$
			bodyEditor.setHtml(value == null ? "" : value.toString()); //$NON-NLS-1$
			return;
		}

		bodyEditor.setEditable(false);
		bodyEditor.setContextElement(null);
		bodyEditor.setHtml(""); //$NON-NLS-1$
	}

	private static Element toElement(Object selected) {
		if (selected instanceof Element) {
			return (Element) selected;
		}
		View notationView = null;
		if (selected instanceof EditPart) {
			Object model = ((EditPart) selected).getModel();
			if (model instanceof View) {
				notationView = (View) model;
			}
		} else if (selected instanceof View) {
			notationView = (View) selected;
		}
		if (notationView != null && notationView.getElement() instanceof Element) {
			return (Element) notationView.getElement();
		}
		if (selected instanceof IAdaptable) {
			Object adapted = ((IAdaptable) selected).getAdapter(Element.class);
			if (adapted instanceof Element) {
				return (Element) adapted;
			}
		}
		Object adapted = Platform.getAdapterManager().getAdapter(selected, Element.class);
		return adapted instanceof Element ? (Element) adapted : null;
	}
}
