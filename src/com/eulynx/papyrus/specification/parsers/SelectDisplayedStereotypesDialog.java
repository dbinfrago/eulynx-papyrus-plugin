package com.eulynx.papyrus.specification.parsers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.gmf.runtime.notation.DecorationNode;
import org.eclipse.gmf.runtime.notation.NotationFactory;
import org.eclipse.gmf.runtime.notation.StringValueStyle;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.papyrus.uml.diagram.common.stereotype.display.helper.StereotypeDisplayCommandExecution;
import org.eclipse.papyrus.uml.diagram.common.stereotype.display.helper.StereotypeDisplayConstant;
import org.eclipse.papyrus.uml.diagram.common.stereotype.display.helper.StereotypeDisplayUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Stereotype;

/**
 * "Select Displayed Stereotypes..." dialog: checkboxes to show or hide each
 * applied stereotype individually in a shape's "«...»" label (e.g. hide
 * "Block" but keep "LogicalStructuralEntity" in "«Block,
 * LogicalStructuralEntity»"), instead of Papyrus's all-or-nothing "Stereotype"
 * toggle. Built on Papyrus's public {@link StereotypeDisplayUtil}/
 * {@link StereotypeDisplayCommandExecution}: every displayed stereotype is its
 * own child notation view (a {@code DecorationNode} of type
 * {@code StereotypeLabel}) that can be shown or hidden independently.
 * <p>
 * That child view is not created for every applied stereotype, so
 * {@link #showOrHideStereotypeLabel} finds the existing label by
 * {@link Stereotype} identity and creates it - in the structure Papyrus itself
 * uses - when none exists yet.
 * <p>
 * Supports several selected elements at once: the checkbox list is the union
 * of all applied stereotypes across the selection, and toggling a checkbox
 * applies to every selected element that has that stereotype applied.
 */
public class SelectDisplayedStereotypesDialog extends TitleAreaDialog {

	private final TransactionalEditingDomain domain;

	/** Every selected (element, its own view) pair with at least one applied stereotype. */
	private final Map<Element, View> targets;

	private final List<Stereotype> stereotypes;

	private CheckboxTableViewer viewer;

	public SelectDisplayedStereotypesDialog(Shell parentShell, TransactionalEditingDomain domain, Map<Element, View> targets) {
		super(parentShell);
		this.domain = domain;
		this.targets = targets;

		Set<Stereotype> allStereotypes = new LinkedHashSet<>();
		for (Element element : targets.keySet()) {
			allStereotypes.addAll(element.getAppliedStereotypes());
		}
		this.stereotypes = new ArrayList<>(allStereotypes);
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Select Displayed Stereotypes"); //$NON-NLS-1$
		setMessage(targets.size() > 1
				? "Choose which applied stereotypes are shown in the labels of the " + targets.size() + " selected elements." //$NON-NLS-1$ //$NON-NLS-2$
				: "Choose which applied stereotypes are shown in this element's label."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);

		viewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.heightHint = 150;
		gridData.widthHint = 300;
		viewer.getControl().setLayoutData(gridData);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((Stereotype) element).getName();
			}
		});
		viewer.setInput(stereotypes);

		for (Stereotype stereotype : stereotypes) {
			// Initial checked state, for a stereotype applied to several
			// selected elements at once: whether the *first* applicable
			// element currently displays it (a mixed initial state across
			// the selection isn't specially indicated - simplest behaviour,
			// matches the common case this was built for: several elements
			// sharing identical stereotypes).
			boolean checked = false;
			for (Map.Entry<Element, View> entry : targets.entrySet()) {
				if (entry.getKey().getAppliedStereotypes().contains(stereotype)) {
					checked = isStereotypeLabelVisible(entry.getValue(), stereotype);
					break;
				}
			}
			viewer.setChecked(stereotype, checked);
		}

		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Select Displayed Stereotypes"); //$NON-NLS-1$
	}

	@Override
	protected void okPressed() {
		for (Stereotype stereotype : stereotypes) {
			boolean shouldShow = viewer.getChecked(stereotype);
			for (Map.Entry<Element, View> entry : targets.entrySet()) {
				if (!entry.getKey().getAppliedStereotypes().contains(stereotype)) {
					// This particular selected element doesn't have this
					// stereotype applied - nothing to toggle for it.
					continue;
				}
				View view = entry.getValue();
				boolean currentlyShown = isStereotypeLabelVisible(view, stereotype);
				if (shouldShow == currentlyShown) {
					continue;
				}
				domain.getCommandStack().execute(new RecordingCommand(domain, "Set Stereotype Display") { //$NON-NLS-1$
					@Override
					protected void doExecute() {
						showOrHideStereotypeLabel(domain, view, stereotype, shouldShow);
					}
				});
			}
		}
		super.okPressed();
	}

	/**
	 * Shows or hides {@code stereotype}'s label on {@code view}, creating the
	 * underlying {@code StereotypeLabel} {@link DecorationNode} first if none
	 * exists yet - see the class comment above for why this can't just
	 * use {@code StereotypeUserActionHelper}'s own "add" command (it only finds
	 * labels that already exist).
	 * Structure matches exactly what Papyrus itself creates for a stereotype
	 * label (confirmed against a real model's `.notation` file): a
	 * {@code DecorationNode} of type {@code "StereotypeLabel"}, a single
	 * {@code StringValueStyle} named {@code "stereotype"} holding the
	 * stereotype's qualified name, and {@code element} pointing at the
	 * {@link Stereotype} itself.
	 */
	private static void showOrHideStereotypeLabel(TransactionalEditingDomain domain, View view, Stereotype stereotype, boolean visible) {
		StereotypeDisplayUtil displayUtil = StereotypeDisplayUtil.getInstance();
		View label = displayUtil.getStereotypeLabel(view, stereotype);
		if (label == null) {
			if (!visible) {
				return; // Nothing to hide - no label exists at all yet.
			}
			DecorationNode newLabel = NotationFactory.eINSTANCE.createDecorationNode();
			newLabel.setType(StereotypeDisplayConstant.STEREOTYPE_LABEL_TYPE);
			newLabel.setElement(stereotype);
			StringValueStyle style = NotationFactory.eINSTANCE.createStringValueStyle();
			style.setName(StereotypeDisplayConstant.STEREOTYPE_LABEL_NAME);
			style.setStringValue(stereotype.getQualifiedName());
			newLabel.getStyles().add(style);
			view.getChildren().add(newLabel);
			label = newLabel;
		}
		StereotypeDisplayCommandExecution.getInstance().setUserVisibility(domain, label, visible);
	}

	/**
	 * Whether {@code stereotype}'s label is actually <b>visible</b> on {@code view}
	 * right now - deliberately not {@code StereotypeDisplayUtil.isLabelExist(View,
	 * Stereotype)}, despite the name sounding exactly like what's needed here. That
	 * method (see its own source) only checks whether a matching {@code
	 * StereotypeLabel} {@link DecorationNode} child is present at all - it does
	 * **not** check {@link View#isVisible()} - so it stays {@code true} forever once
	 * a label has been created even once, hidden or not.
	 */
	private static boolean isStereotypeLabelVisible(View view, Stereotype stereotype) {
		View label = StereotypeDisplayUtil.getInstance().getStereotypeLabel(view, stereotype);
		return label != null && label.isVisible();
	}
}
