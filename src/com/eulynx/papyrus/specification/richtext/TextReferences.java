package com.eulynx.papyrus.specification.richtext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Live references to model elements inside <i>plain</i> UML text (guard,
 * effect, entry/exit/do, change/time expression of a transition or state).
 * The text itself stays an ordinary string (diagram, sync and export read it
 * unchanged); the positions of the referenced names plus the element URIs live
 * in an {@link EAnnotation} on the element that holds the text. On a rename
 * {@link #refresh} rebuilds the text with the current names.
 * <p>
 * The annotation also keeps the text it belongs to. If the text no longer
 * equals that copy (someone edited it outside our dialogs, e.g. in the
 * Properties view), the references are considered stale and dropped - the
 * text then simply stays as written.
 */
public final class TextReferences {

	// Stored in the model; the "statemachine.labels" prefix is kept so existing models stay readable.
	public static final String SOURCE = "com.eulynx.papyrus.statemachine.labels.textReferences"; //$NON-NLS-1$

	private static final String KEY_TEXT = "text"; //$NON-NLS-1$

	private static final String KEY_REFS = "refs"; //$NON-NLS-1$

	private TextReferences() {
	}

	/** One reference: {@code length} characters from {@code start} show the name of the element with {@code uri}. */
	public static final class Ref {

		public final int start;

		public final int length;

		public final String uri;

		public Ref(int start, int length, String uri) {
			this.start = start;
			this.length = length;
			this.uri = uri;
		}
	}

	/** A text together with its references (plain data, safe to keep after a dialog is disposed). */
	public static final class Value {

		public final String text;

		public final List<Ref> refs;

		public Value(String text, List<Ref> refs) {
			this.text = text;
			this.refs = refs;
		}
	}

	public static boolean hasData(Element host) {
		return host != null && host.getEAnnotation(SOURCE) != null;
	}

	/** The references stored for {@code host}, or an empty list if there are none or {@code currentText} differs from the stored text. */
	public static List<Ref> load(Element host, String currentText) {
		List<Ref> result = new ArrayList<>();
		if (host == null || currentText == null) {
			return result;
		}
		EAnnotation annotation = host.getEAnnotation(SOURCE);
		if (annotation == null || !currentText.equals(annotation.getDetails().get(KEY_TEXT))) {
			return result;
		}
		String encoded = annotation.getDetails().get(KEY_REFS);
		if (encoded == null || encoded.isEmpty()) {
			return result;
		}
		for (String line : encoded.split("\n")) { //$NON-NLS-1$
			String[] parts = line.split(":", 3); //$NON-NLS-1$
			if (parts.length == 3) {
				try {
					int start = Integer.parseInt(parts[0]);
					int length = Integer.parseInt(parts[1]);
					if (start >= 0 && length > 0 && start + length <= currentText.length()) {
						result.add(new Ref(start, length, parts[2]));
					}
				} catch (NumberFormatException e) {
					// ignore a damaged entry
				}
			}
		}
		result.sort(Comparator.comparingInt(r -> r.start));
		return result;
	}

	/** Stores {@code refs} for {@code text} on {@code host}; no references removes the annotation. */
	public static void store(Element host, String text, List<Ref> refs) {
		if (host == null) {
			return;
		}
		EAnnotation annotation = host.getEAnnotation(SOURCE);
		if (refs == null || refs.isEmpty()) {
			if (annotation != null) {
				host.getEAnnotations().remove(annotation);
			}
			return;
		}
		if (annotation == null) {
			annotation = host.createEAnnotation(SOURCE);
		}
		StringBuilder encoded = new StringBuilder();
		for (Ref ref : refs) {
			if (encoded.length() > 0) {
				encoded.append('\n');
			}
			encoded.append(ref.start).append(':').append(ref.length).append(':').append(ref.uri);
		}
		annotation.getDetails().put(KEY_TEXT, text);
		annotation.getDetails().put(KEY_REFS, encoded.toString());
	}

	/** The element a reference points to, or null. */
	public static NamedElement resolve(String uri, ResourceSet resourceSet) {
		if (resourceSet == null) {
			return null;
		}
		try {
			EObject target = resourceSet.getEObject(URI.createURI(uri), false);
			return target instanceof NamedElement && !target.eIsProxy() ? (NamedElement) target : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Rebuilds {@code currentText} with the current names of the referenced
	 * elements and updates the annotation of {@code host}.
	 *
	 * @return the new text, or null if nothing has to change (including the
	 *         case that stale references were just dropped)
	 */
	public static String refresh(Element host, String currentText, ResourceSet resourceSet) {
		if (!hasData(host) || currentText == null) {
			return null;
		}
		List<Ref> refs = load(host, currentText);
		if (refs.isEmpty()) {
			store(host, currentText, refs);
			return null;
		}
		StringBuilder text = new StringBuilder();
		List<Ref> updated = new ArrayList<>();
		int position = 0;
		for (Ref ref : refs) {
			if (ref.start < position) {
				continue;
			}
			text.append(currentText, position, ref.start);
			String name = null;
			NamedElement target = resolve(ref.uri, resourceSet);
			if (target != null && target.getName() != null && !target.getName().isEmpty()) {
				name = target.getName();
			}
			if (name == null) {
				name = currentText.substring(ref.start, ref.start + ref.length);
			}
			updated.add(new Ref(text.length(), name.length(), ref.uri));
			text.append(name);
			position = ref.start + ref.length;
		}
		text.append(currentText, position, currentText.length());
		String result = text.toString();
		store(host, result, updated);
		return result.equals(currentText) ? null : result;
	}
}
