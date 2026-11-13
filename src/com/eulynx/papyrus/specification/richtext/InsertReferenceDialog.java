// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.richtext;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Filterable tree picker to choose a model element, used by the editors'
 * "Insert Reference..." buttons.
 */
public class InsertReferenceDialog extends TitleAreaDialog {

	private final EObject contextElement;

	private EObject selectedElement;

	public InsertReferenceDialog(Shell parentShell, EObject contextElement) {
		super(parentShell);
		this.contextElement = contextElement;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("Insert Reference"); //$NON-NLS-1$
		setMessage("Select a model element to insert as a live reference."); //$NON-NLS-1$
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		PatternFilter filter = new PatternFilter();
		FilteredTree filteredTree = new FilteredTree(composite, SWT.SINGLE | SWT.BORDER, filter, true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 400;
		data.widthHint = 400;
		filteredTree.setLayoutData(data);

		TreeViewer viewer = filteredTree.getViewer();
		viewer.setContentProvider(new ModelTreeContentProvider());
		viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof NamedElement) {
					String name = ((NamedElement) element).getName();
					if (name != null && !name.isEmpty()) {
						return name;
					}
				}
				if (element instanceof EObject) {
					return "(" + ((EObject) element).eClass().getName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				return String.valueOf(element);
			}
		});
		viewer.setInput(contextElement.eResource().getResourceSet());
		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			Object first = selection.getFirstElement();
			selectedElement = first instanceof EObject ? (EObject) first : null;
			getButton(IDialogConstants.OK_ID).setEnabled(selectedElement instanceof NamedElement);
		});

		return composite;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		getButton(IDialogConstants.OK_ID).setEnabled(false);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Insert Reference"); //$NON-NLS-1$
	}

	public EObject getSelectedElement() {
		return selectedElement;
	}

	private static final class ModelTreeContentProvider implements ITreeContentProvider {

		@Override
		public Object[] getElements(Object inputElement) {
			return getChildren(inputElement);
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof ResourceSet) {
				List<EObject> roots = new ArrayList<>();
				for (Resource resource : ((ResourceSet) parentElement).getResources()) {
					roots.addAll(resource.getContents());
				}
				return roots.toArray();
			}
			if (parentElement instanceof EObject) {
				return ((EObject) parentElement).eContents().toArray();
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return element instanceof EObject ? ((EObject) element).eContainer() : null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}
	}
}
