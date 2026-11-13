// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.richtext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Pure-string counterpart to {@link HtmlStyledTextEditor}'s reference
 * resolution, usable without an SWT widget: given a stored rich-text HTML
 * string, re-resolves every {@code data-ref-uri} span against the model and
 * rewrites its displayed name to the target's *current* name if it has
 * drifted (e.g. after a rename in Model Explorer). Used by
 * {@link RichTextReferenceSync} to keep every Requirement/Comment in a model
 * in sync automatically, instead of requiring each one to be individually
 * re-opened and saved.
 */
public final class RichTextReferenceUtil {

	private static final Pattern SPAN_PATTERN = Pattern
			.compile("(<span[^>]*data-ref-uri=\"([^\"]*)\"[^>]*>)([^<]*)(</span>)", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private RichTextReferenceUtil() {
	}

	/**
	 * @return {@code html} with every reference span's displayed name refreshed
	 *         to the target's current name, or {@code html} itself (same
	 *         reference) if nothing needed updating - callers can use that to
	 *         cheaply detect "no change".
	 */
	public static String refresh(String html, ResourceSet resourceSet) {
		if (html == null || html.isEmpty() || html.indexOf("data-ref-uri") < 0) { //$NON-NLS-1$
			return html;
		}
		Matcher matcher = SPAN_PATTERN.matcher(html);
		StringBuilder result = new StringBuilder();
		boolean changed = false;
		int lastEnd = 0;
		while (matcher.find()) {
			result.append(html, lastEnd, matcher.start());
			String uri = decodeEntities(matcher.group(2));
			String currentText = matcher.group(3);
			String liveName = resolveName(uri, resourceSet);
			if (liveName != null && !liveName.equals(decodeEntities(currentText))) {
				result.append(matcher.group(1)).append(escape(liveName)).append(matcher.group(4));
				changed = true;
			} else {
				result.append(matcher.group(1)).append(currentText).append(matcher.group(4));
			}
			lastEnd = matcher.end();
		}
		result.append(html, lastEnd, html.length());
		return changed ? result.toString() : html;
	}

	private static String resolveName(String uri, ResourceSet resourceSet) {
		try {
			EObject resolved = resourceSet.getEObject(URI.createURI(uri), false);
			if (resolved instanceof NamedElement) {
				String name = ((NamedElement) resolved).getName();
				return name == null || name.isEmpty() ? null : name;
			}
		} catch (RuntimeException e) {
			// Unresolvable URI (e.g. the target was deleted) - leave the stored text as-is.
		}
		return null;
	}

	private static String decodeEntities(String text) {
		if (text.indexOf('&') < 0) {
			return text;
		}
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				.replace("&apos;", "'").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static String escape(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
			case '&':
				out.append("&amp;"); //$NON-NLS-1$
				break;
			case '<':
				out.append("&lt;"); //$NON-NLS-1$
				break;
			case '>':
				out.append("&gt;"); //$NON-NLS-1$
				break;
			default:
				out.append(c);
			}
		}
		return out.toString();
	}
}
