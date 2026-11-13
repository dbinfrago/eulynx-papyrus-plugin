package com.eulynx.papyrus.specification.message;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.uml2.uml.Message;

import com.eulynx.papyrus.specification.richtext.RichTextReferenceUtil;
import com.eulynx.papyrus.specification.timing.DurationArrowSupport;

/**
 * Speicherung des Rich-Text-Labels einer Message: der HTML-Text (mit
 * Referenz-Spans) liegt in einer EAnnotation an der Message, der reine Text
 * steht im Namen der Message (den Papyrus ohne Signature direkt anzeigt).
 */
public final class MessageLabelSupport {

	/** EAnnotation-Source an der Message. */
	// Stored in the model; the "statemachine.labels" prefix is kept so existing models stay readable.
	public static final String ANNOTATION_SOURCE = "com.eulynx.papyrus.statemachine.labels.messageLabel"; //$NON-NLS-1$

	/** Detail-Key mit dem HTML-Text. */
	public static final String KEY_HTML = "html"; //$NON-NLS-1$

	private MessageLabelSupport() {
	}

	/** Gespeicherter HTML-Text oder {@code null}. */
	public static String getHtml(Message message) {
		EAnnotation annotation = message.getEAnnotation(ANNOTATION_SOURCE);
		return annotation == null ? null : annotation.getDetails().get(KEY_HTML);
	}

	/** Reiner Text ohne Markup, einzeilig. */
	public static String plainText(String html) {
		return DurationArrowSupport.plainText(html);
	}

	/**
	 * Schreibt HTML und Namen. Muss in einem Schreib-Command laufen.
	 * Signature und Argumente werden entfernt, sonst zeigt Papyrus statt des
	 * Namens weiter den Signal-Aufruf an.
	 */
	public static void apply(Message message, String html) {
		EAnnotation annotation = message.getEAnnotation(ANNOTATION_SOURCE);
		if (annotation == null) {
			annotation = message.createEAnnotation(ANNOTATION_SOURCE);
		}
		annotation.getDetails().put(KEY_HTML, html);
		message.setName(plainText(html));
		message.setSignature(null);
		message.getArguments().clear();
	}

	/**
	 * Frischt die Referenznamen auf; gibt {@code true} zurück, wenn sich etwas
	 * geändert hat. Muss in einem Schreib-Command laufen.
	 */
	public static boolean refresh(Message message, ResourceSet resourceSet) {
		String html = getHtml(message);
		if (html == null) {
			return false;
		}
		String updated = RichTextReferenceUtil.refresh(html, resourceSet);
		if (updated == html) {
			return false;
		}
		message.getEAnnotation(ANNOTATION_SOURCE).getDetails().put(KEY_HTML, updated);
		message.setName(plainText(updated));
		return true;
	}
}
