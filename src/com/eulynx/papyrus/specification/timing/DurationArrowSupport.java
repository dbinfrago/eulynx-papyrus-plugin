// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.timing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eulynx.papyrus.specification.richtext.RichTextReferenceUtil;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Shared label logic of the duration between two events
 * ({@link DurationLinkSupport}): the operator list, and turning operator +
 * expression into the text shown at the arrow, {@code {<operator> <expression>}}
 * - nothing before or after the braces.
 *
 * <p>
 * The expression is HTML in the plugin's rich-text dialect. References are
 * {@code data-ref-uri} spans, so the label always shows the *current* name of
 * the referenced element (same mechanism as Requirement/Comment text).
 */
public final class DurationArrowSupport {

	public static final String KEY_OPERATOR = "operator"; //$NON-NLS-1$

	public static final String KEY_EXPRESSION = "expression"; //$NON-NLS-1$

	/** Selectable operators; the empty string means "no operator", i.e. just {@code {expression}}. */
	public static final String[] OPERATORS = { "", "<", "<=", "=", ">", ">=" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	private static final Pattern REF_URI_PATTERN = Pattern.compile("data-ref-uri\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static final Pattern LINE_BREAKS = Pattern.compile("<br\\s*/?>|</p>|</div>", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>"); //$NON-NLS-1$

	private DurationArrowSupport() {
	}

	/**
	 * {@code {<operator> <expression>}}.
	 *
	 * @param resourceSet
	 *            used to resolve references to their current names; may be null
	 */
	public static String buildLabel(String operator, String expressionHtml, ResourceSet resourceSet) {
		String op = operator == null ? "" : operator; //$NON-NLS-1$
		String html = expressionHtml == null ? "" : expressionHtml; //$NON-NLS-1$
		if (resourceSet != null) {
			html = RichTextReferenceUtil.refresh(html, resourceSet);
		}
		String expression = plainText(html);

		StringBuilder label = new StringBuilder("{"); //$NON-NLS-1$
		label.append(op);
		if (!op.isEmpty() && !expression.isEmpty()) {
			label.append(' ');
		}
		label.append(expression).append('}');
		return label.toString();
	}

	/** All model elements referenced from the expression (for rename listeners). */
	public static List<EObject> referencedElements(String expressionHtml, ResourceSet resourceSet) {
		List<EObject> result = new ArrayList<>();
		if (resourceSet == null || expressionHtml == null) {
			return result;
		}
		Matcher matcher = REF_URI_PATTERN.matcher(expressionHtml);
		while (matcher.find()) {
			try {
				EObject target = resourceSet.getEObject(URI.createURI(matcher.group(1).replace("&amp;", "&")), false); //$NON-NLS-1$ //$NON-NLS-2$
				if (target != null) {
					result.add(target);
				}
			} catch (RuntimeException e) {
				// Unresolvable reference (target deleted): simply not listened to.
			}
		}
		return result;
	}

	/** The expression without markup, on a single line. */
	public static String plainText(String html) {
		if (html == null || html.isEmpty()) {
			return ""; //$NON-NLS-1$
		}
		String text = LINE_BREAKS.matcher(html).replaceAll(" "); //$NON-NLS-1$
		text = ANY_TAG.matcher(text).replaceAll(""); //$NON-NLS-1$
		text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
				.replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		return text.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
