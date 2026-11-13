// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.timing;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.gmf.runtime.common.core.command.ICommand;
import org.eclipse.gmf.runtime.common.core.command.UnexecutableCommand;
import org.eclipse.gmf.runtime.common.ui.services.parser.IParserEditStatus;
import org.eclipse.gmf.runtime.common.ui.services.parser.ParserEditStatus;
import org.eclipse.gmf.runtime.emf.ui.services.parser.ISemanticParser;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.uml2.uml.DurationConstraint;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.UMLPackage;

/**
 * Label text of a duration link created by {@link DurationLinkSupport}:
 * {@code {<operator> <expression>}}. Read-only (edited through "Edit Duration
 * Arrow..."), and it asks the label edit part to listen to the referenced
 * elements too, so a rename of a referenced Block updates the label.
 */
public class DurationLinkParser implements ISemanticParser {

	@Override
	public String getPrintString(IAdaptable element, int flags) {
		EObject target = element.getAdapter(EObject.class);
		if (DurationLinkSupport.hasData(target)) {
			return DurationLinkSupport.label((DurationConstraint) target);
		}
		if (target instanceof NamedElement && ((NamedElement) target).getName() != null) {
			return ((NamedElement) target).getName();
		}
		return ""; //$NON-NLS-1$
	}

	@Override
	public String getEditString(IAdaptable element, int flags) {
		return getPrintString(element, flags);
	}

	@Override
	public IParserEditStatus isValidEditString(IAdaptable element, String editString) {
		return ParserEditStatus.UNEDITABLE_STATUS;
	}

	@Override
	public ICommand getParseCommand(IAdaptable element, String newString, int flags) {
		return UnexecutableCommand.INSTANCE;
	}

	@Override
	public boolean isAffectingEvent(Object event, int flags) {
		return isRelevant(event);
	}

	@Override
	public IContentAssistProcessor getCompletionProcessor(IAdaptable element) {
		return null;
	}

	@Override
	public List<EObject> getSemanticElementsBeingParsed(EObject element) {
		List<EObject> result = new ArrayList<>();
		result.add(element);
		if (element instanceof DurationConstraint && DurationLinkSupport.hasData(element)) {
			result.addAll(DurationLinkSupport.referencedElements((DurationConstraint) element));
		}
		return result;
	}

	@Override
	public boolean areSemanticElementsAffected(EObject listener, Object notification) {
		return isRelevant(notification);
	}

	private boolean isRelevant(Object event) {
		if (!(event instanceof Notification)) {
			return false;
		}
		Notification notification = (Notification) event;
		Object feature = notification.getFeature();
		return feature == UMLPackage.Literals.NAMED_ELEMENT__NAME
				|| feature == EcorePackage.Literals.EMODEL_ELEMENT__EANNOTATIONS
				|| notification.getNotifier() instanceof EAnnotation;
	}
}
