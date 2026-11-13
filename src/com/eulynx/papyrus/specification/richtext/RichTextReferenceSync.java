// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.richtext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.LiteralString;
import org.eclipse.uml2.uml.Message;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLPackage;

import com.eulynx.papyrus.specification.message.MessageLabelSupport;

/**
 * Keeps every rich-text {@code Comment.body}/{@code «Requirement».text} in a
 * model's resource set in sync whenever a referenced element is renamed - the
 * automatic counterpart to opening "Edit Comment..."/"Edit Requirement..."
 * (or the Rich Text Preview view) and pressing Save, which previously had to
 * be done by hand, per referencing element, after every rename. With many
 * diagrams/comments this was impractical and error-prone to remember, so this
 * listener does it for the whole model in one step, automatically.
 */
public final class RichTextReferenceSync extends TriggerListener {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	public RichTextReferenceSync() {
		super(NotificationFilter.createFeatureFilter(UMLPackage.Literals.NAMED_ELEMENT__NAME));
	}

	@Override
	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
		if (Objects.equals(notification.getOldValue(), notification.getNewValue())) {
			return null;
		}
		ResourceSet resourceSet = domain.getResourceSet();
		if (resourceSet == null) {
			return null;
		}
		return new RecordingCommand(domain, "Refresh Rich Text References") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				refreshAll(domain, resourceSet);
			}
		};
	}

	private static void refreshAll(TransactionalEditingDomain domain, ResourceSet resourceSet) {
		List<Resource> resources = new ArrayList<>(resourceSet.getResources());
		for (Resource resource : resources) {
			if (!isWritable(domain, resource)) {
				continue;
			}
			TreeIterator<EObject> contents = resource.getAllContents();
			while (contents.hasNext()) {
				EObject content = contents.next();
				if (content instanceof Comment) {
					refreshComment((Comment) content, resourceSet);
				} else if (content instanceof Message) {
					MessageLabelSupport.refresh((Message) content, resourceSet);
				} else if (content instanceof LiteralString) {
					refreshLiteral((LiteralString) content, resourceSet);
				} else if (content instanceof OpaqueBehavior && TextReferences.hasData((OpaqueBehavior) content)) {
					refreshBehavior((OpaqueBehavior) content, resourceSet);
				} else if (content instanceof Element) {
					refreshRequirement((Element) content, resourceSet);
				}
			}
		}
	}

	/**
	 * Only workspace and file resources are user models; {@code pathmap:} and
	 * {@code platform:/plugin} libraries and profiles are read-only, and writing
	 * to them aborts the whole transaction.
	 */
	private static boolean isWritable(TransactionalEditingDomain domain, Resource resource) {
		URI uri = resource.getURI();
		return uri != null && (uri.isPlatformResource() || uri.isFile()) && !domain.isReadOnly(resource);
	}

	/** Guard / change- or time-expression of a transition with live references ({@link TextReferences}). */
	private static void refreshLiteral(LiteralString literal, ResourceSet resourceSet) {
		if (!TextReferences.hasData(literal)) {
			return;
		}
		String updated = TextReferences.refresh(literal, literal.getValue(), resourceSet);
		if (updated != null) {
			literal.setValue(updated);
		}
	}

	/** Effect / entry / exit / doActivity body with live references ({@link TextReferences}). */
	private static void refreshBehavior(OpaqueBehavior behavior, ResourceSet resourceSet) {
		if (behavior.getBodies().isEmpty()) {
			return;
		}
		String updated = TextReferences.refresh(behavior, behavior.getBodies().get(0), resourceSet);
		if (updated != null) {
			behavior.getBodies().set(0, updated);
		}
	}

	private static void refreshComment(Comment comment, ResourceSet resourceSet) {
		String updated = RichTextReferenceUtil.refresh(comment.getBody(), resourceSet);
		if (updated != comment.getBody()) {
			comment.setBody(updated);
		}
	}

	private static void refreshRequirement(Element element, ResourceSet resourceSet) {
		Stereotype stereotype = element.getAppliedStereotype(REQUIREMENT_STEREOTYPE);
		if (stereotype == null) {
			return;
		}
		Object value = element.getValue(stereotype, "text"); //$NON-NLS-1$
		if (!(value instanceof String)) {
			return;
		}
		String updated = RichTextReferenceUtil.refresh((String) value, resourceSet);
		if (updated != value) {
			element.setValue(stereotype, "text", updated); //$NON-NLS-1$
		}
	}
}
