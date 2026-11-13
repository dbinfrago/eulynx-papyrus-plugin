// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.richtext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Lightweight formatted-text editor: a Bold/Italic/Bullet-list/Insert-Reference
 * toolbar over a plain SWT {@link StyledText}. Persists formatting as plain HTML
 * ({@code <b>}, {@code <em>}, one {@code <p>} per bulleted line, and {@code
 * <u><span style="color: rgb(...)" data-ref-uri="...">} for live element
 * references) - chosen over a Markdown-subset or an embedded CKEditor browser
 * because the downstream consumer (Polarion, and Capella's own Description field
 * for comparison) render rich text as HTML/XHTML.
 * <p>
 * The exact tag subset above is deliberate, not arbitrary: it's the closed set
 * Papyrus's own {@code HTMLCornerBentFigure} (which renders Comment/Note
 * bodies directly on diagrams, e.g. Sequence Diagrams) understands - anything
 * else (its {@code <i>}, {@code <a>}, {@code <ul>/<li>}) is silently dropped by
 * that renderer. Reading also accepts the older {@code <i>}/{@code <a
 * href>}/{@code <ul><li>} forms of already-saved content.
 * <p>
 * A reference span's target is identified by the referenced element's EMF
 * {@link URI} (robust across renames, unlike a name-text search/replace). The
 * *displayed* name is re-resolved to the target's *current* name every time
 * {@link #setHtml(String)} runs (i.e. whenever this editor is opened) and
 * whenever {@link #getHtml()} is read (i.e. on save) - so a stale name only
 * lingers until the requirement is next opened or saved, not forever. If the
 * target can no longer be resolved (e.g. deleted), the span is shown
 * struck-through in red instead of silently keeping the old name.
 */
public class HtmlStyledTextEditor extends Composite {

	private static final Pattern TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z]+)([^>]*)>"); //$NON-NLS-1$

	private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static final Pattern REF_URI_ATTR_PATTERN = Pattern.compile("data-ref-uri\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private final StyledText styledText;

	private EObject contextElement;

	private final Composite toolbar;

	/**
	 * @param contextElement the element whose resource/resource-set is used to
	 *                       resolve reference targets (typically the Requirement
	 *                       being edited).
	 */
	public HtmlStyledTextEditor(Composite parent, int style, EObject contextElement) {
		super(parent, SWT.NONE);
		this.contextElement = contextElement;
		setLayout(new GridLayout(1, false));

		toolbar = new Composite(this, SWT.NONE);
		toolbar.setLayout(new RowLayout());

		Button boldButton = new Button(toolbar, SWT.PUSH);
		boldButton.setText("B"); //$NON-NLS-1$
		boldButton.setToolTipText("Bold"); //$NON-NLS-1$
		boldButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> toggleStyle(SWT.BOLD)));

		Button italicButton = new Button(toolbar, SWT.PUSH);
		italicButton.setText("I"); //$NON-NLS-1$
		italicButton.setToolTipText("Italic"); //$NON-NLS-1$
		italicButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> toggleStyle(SWT.ITALIC)));

		Button bulletButton = new Button(toolbar, SWT.PUSH);
		bulletButton.setText("• List"); //$NON-NLS-1$
		bulletButton.setToolTipText("Bullet List"); //$NON-NLS-1$
		bulletButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> toggleBullets()));

		Button referenceButton = new Button(toolbar, SWT.PUSH);
		referenceButton.setText("Insert Reference..."); //$NON-NLS-1$
		referenceButton.setToolTipText("Insert a live reference to a model element"); //$NON-NLS-1$
		referenceButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> insertReference()));

		styledText = new StyledText(this, style | SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		styledText.setLayoutData(data);
	}

	/**
	 * Changes which element's resource/resource-set reference targets are
	 * resolved against - needed when this editor is reused across a changing
	 * selection (e.g. in a long-lived view) rather than created fresh per
	 * element (e.g. in a dialog).
	 */
	public void setContextElement(EObject contextElement) {
		this.contextElement = contextElement;
	}

	public void setEditable(boolean editable) {
		styledText.setEditable(editable);
		setEnabled(editable);
	}

	/** The toolbar Composite (RowLayout) holding the B/I/List/Insert-Reference buttons - callers may add further buttons to the same row. */
	public Composite getToolbar() {
		return toolbar;
	}

	/** Replaces the current content, parsing {@code html} into text + styling. */
	public void setHtml(String html) {
		String input = html == null ? "" : html; //$NON-NLS-1$
		StringBuilder plain = new StringBuilder();
		List<int[]> boldRanges = new ArrayList<>();
		List<int[]> italicRanges = new ArrayList<>();
		List<int[]> refRanges = new ArrayList<>();
		List<String> refUris = new ArrayList<>();

		int boldStart = -1;
		int italicStart = -1;
		int refStart = -1;
		String refUri = null;

		Matcher matcher = TAG_PATTERN.matcher(input);
		int lastEnd = 0;
		while (matcher.find()) {
			if (matcher.start() > lastEnd) {
				plain.append(decodeEntities(input.substring(lastEnd, matcher.start())));
			}
			boolean closing = "/".equals(matcher.group(1)); //$NON-NLS-1$
			String tag = matcher.group(2).toLowerCase(Locale.ROOT);
			String attrs = matcher.group(3);
			switch (tag) {
			case "b": //$NON-NLS-1$
			case "strong": //$NON-NLS-1$
				if (!closing) {
					boldStart = plain.length();
				} else if (boldStart >= 0) {
					boldRanges.add(new int[] { boldStart, plain.length() });
					boldStart = -1;
				}
				break;
			case "i": //$NON-NLS-1$
			case "em": //$NON-NLS-1$
				if (!closing) {
					italicStart = plain.length();
				} else if (italicStart >= 0) {
					italicRanges.add(new int[] { italicStart, plain.length() });
					italicStart = -1;
				}
				break;
			case "a": //$NON-NLS-1$
				if (!closing) {
					Matcher hrefMatcher = HREF_PATTERN.matcher(attrs);
					refUri = hrefMatcher.find() ? hrefMatcher.group(1) : ""; //$NON-NLS-1$
					refStart = plain.length();
				} else if (refStart >= 0) {
					refRanges.add(new int[] { refStart, plain.length() });
					refUris.add(refUri);
					refStart = -1;
					refUri = null;
				}
				break;
			case "span": //$NON-NLS-1$
				if (!closing) {
					Matcher refUriMatcher = REF_URI_ATTR_PATTERN.matcher(attrs);
					if (refUriMatcher.find()) {
						refUri = decodeEntities(refUriMatcher.group(1));
						refStart = plain.length();
					}
				} else if (refStart >= 0) {
					refRanges.add(new int[] { refStart, plain.length() });
					refUris.add(refUri);
					refStart = -1;
					refUri = null;
				}
				break;
			case "li": //$NON-NLS-1$
				if (!closing) {
					if (plain.length() > 0 && plain.charAt(plain.length() - 1) != '\n') {
						plain.append('\n');
					}
					plain.append("• "); //$NON-NLS-1$
				}
				break;
			case "br": //$NON-NLS-1$
			case "p": //$NON-NLS-1$
			case "div": //$NON-NLS-1$
				if (!closing && plain.length() > 0 && plain.charAt(plain.length() - 1) != '\n') {
					plain.append('\n');
				}
				break;
			default:
				break;
			}
			lastEnd = matcher.end();
		}
		if (lastEnd < input.length()) {
			plain.append(decodeEntities(input.substring(lastEnd)));
		}

		applyResolvedText(plain.toString(), boldRanges, italicRanges, refRanges, refUris);
	}

	/** Reconstructs the HTML string from the current text + styling. */
	public String getHtml() {
		String text = styledText.getText();
		boolean[] bold = computeStyleArray(SWT.BOLD);
		boolean[] italic = computeStyleArray(SWT.ITALIC);

		Map<Integer, List<String>> opens = new TreeMap<>();
		Map<Integer, List<String>> closes = new TreeMap<>();
		for (StyleRange range : styledText.getStyleRanges()) {
			if (range.data instanceof ReferenceData) {
				String uri = ((ReferenceData) range.data).uri;
				opens.computeIfAbsent(range.start, k -> new ArrayList<>()).add("<u><span style=\"color: rgb(0, 102, 204)\" data-ref-uri=\"" + escapeAttribute(uri) + "\">"); //$NON-NLS-1$ //$NON-NLS-2$
				closes.computeIfAbsent(range.start + range.length, k -> new ArrayList<>()).add("</span></u>"); //$NON-NLS-1$
			}
		}

		StringBuilder out = new StringBuilder();
		int lineCount = styledText.getLineCount();
		for (int line = 0; line < lineCount; line++) {
			int lineStart = styledText.getOffsetAtLine(line);
			String lineContent = styledText.getLine(line);
			boolean bulleted = lineContent.startsWith("• "); //$NON-NLS-1$
			int contentEnd = lineStart + lineContent.length();

			if (bulleted) {
				out.append("<p>"); //$NON-NLS-1$
			} else if (line > 0) {
				out.append("<br/>"); //$NON-NLS-1$
			}

			appendStyledSegment(out, text, lineStart, contentEnd, bold, italic, opens, closes);

			if (bulleted) {
				out.append("</p>"); //$NON-NLS-1$
			}
		}
		return out.toString();
	}

	private static void appendStyledSegment(StringBuilder out, String text, int start, int end, boolean[] bold, boolean[] italic, Map<Integer, List<String>> opens, Map<Integer, List<String>> closes) {
		boolean curBold = false;
		boolean curItalic = false;
		for (int i = start; i <= end; i++) {
			if (closes.containsKey(i)) {
				closes.get(i).forEach(out::append);
			}
			boolean wantBold = i < end && bold[i];
			boolean wantItalic = i < end && italic[i];
			if (wantBold != curBold) {
				out.append(curBold ? "</b>" : "<b>"); //$NON-NLS-1$ //$NON-NLS-2$
				curBold = wantBold;
			}
			if (wantItalic != curItalic) {
				out.append(curItalic ? "</em>" : "<em>"); //$NON-NLS-1$ //$NON-NLS-2$
				curItalic = wantItalic;
			}
			if (opens.containsKey(i)) {
				opens.get(i).forEach(out::append);
			}
			if (i < end) {
				appendEscaped(out, text.charAt(i));
			}
		}
	}

	private static void appendEscaped(StringBuilder out, char c) {
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

	private static String escapeAttribute(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static String decodeEntities(String text) {
		if (text.indexOf('&') < 0) {
			return text;
		}
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
	}

	private void insertReference() {
		InsertReferenceDialog dialog = new InsertReferenceDialog(getShell(), contextElement);
		if (dialog.open() != Window.OK) {
			return;
		}
		EObject selected = dialog.getSelectedElement();
		if (!(selected instanceof NamedElement)) {
			return;
		}
		String name = ((NamedElement) selected).getName();
		if (name == null || name.isEmpty()) {
			name = "(unnamed)"; //$NON-NLS-1$
		}
		String uri = EcoreUtil.getURI(selected).toString();

		Point selection = styledText.getSelectionRange();
		int insertPos = selection.x;
		styledText.replaceTextRange(insertPos, selection.y, name);

		StyleRange range = newReferenceStyleRange(insertPos, name.length(), uri, false);
		List<StyleRange> combined = new ArrayList<>(Arrays.asList(styledText.getStyleRanges()));
		combined.add(range);
		combined.sort(Comparator.comparingInt(r -> r.start));
		styledText.setStyleRanges(combined.toArray(new StyleRange[0]));
		styledText.setSelectionRange(insertPos + name.length(), 0);
		styledText.setFocus();
	}

	private void toggleStyle(int styleBit) {
		Point selection = styledText.getSelectionRange();
		if (selection.y <= 0) {
			return;
		}
		boolean[] bold = computeStyleArray(SWT.BOLD);
		boolean[] italic = computeStyleArray(SWT.ITALIC);
		boolean[] target = styleBit == SWT.BOLD ? bold : italic;

		int start = selection.x;
		int end = Math.min(start + selection.y, target.length);
		boolean allSet = true;
		for (int i = start; i < end; i++) {
			if (!target[i]) {
				allSet = false;
				break;
			}
		}
		boolean newValue = !allSet;
		for (int i = start; i < end; i++) {
			target[i] = newValue;
		}

		List<StyleRange> combined = new ArrayList<>(Arrays.asList(mergeStyleArrays(bold, italic)));
		for (StyleRange range : styledText.getStyleRanges()) {
			if (range.data instanceof ReferenceData) {
				combined.add(range);
			}
		}
		combined.sort(Comparator.comparingInt(r -> r.start));
		styledText.setStyleRanges(combined.toArray(new StyleRange[0]));
		styledText.setSelectionRange(start, selection.y);
		styledText.setFocus();
	}

	private void toggleBullets() {
		Point selection = styledText.getSelectionRange();
		int firstLine = styledText.getLineAtOffset(selection.x);
		int lastLine = styledText.getLineAtOffset(selection.x + Math.max(selection.y, 0));

		boolean allBulleted = true;
		for (int line = firstLine; line <= lastLine; line++) {
			if (!styledText.getLine(line).startsWith("• ")) { //$NON-NLS-1$
				allBulleted = false;
				break;
			}
		}

		for (int line = lastLine; line >= firstLine; line--) {
			int lineOffset = styledText.getOffsetAtLine(line);
			String content = styledText.getLine(line);
			if (allBulleted) {
				if (content.startsWith("• ")) { //$NON-NLS-1$
					styledText.replaceTextRange(lineOffset, 2, ""); //$NON-NLS-1$
				}
			} else if (!content.startsWith("• ")) { //$NON-NLS-1$
				styledText.replaceTextRange(lineOffset, 0, "• "); //$NON-NLS-1$
			}
		}

		int firstLineOffset = styledText.getOffsetAtLine(firstLine);
		int newCaret = Math.max(firstLineOffset, selection.x + (allBulleted ? -2 : 2));
		styledText.setSelectionRange(newCaret, 0);
		styledText.setFocus();
	}

	private EObject resolveUri(String uriString) {
		try {
			if (contextElement.eResource() == null) {
				return null;
			}
			ResourceSet resourceSet = contextElement.eResource().getResourceSet();
			if (resourceSet == null) {
				return null;
			}
			return resourceSet.getEObject(URI.createURI(uriString), false);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Splices resolved (raw-coordinate) bold/italic/reference spans into the
	 * StyledText, remapping bold/italic offsets to account for reference spans
	 * whose live display name differs in length from what was persisted.
	 */
	private void applyResolvedText(String rawPlain, List<int[]> boldRanges, List<int[]> italicRanges, List<int[]> refRanges, List<String> refUris) {
		List<int[]> order = new ArrayList<>();
		for (int idx = 0; idx < refRanges.size(); idx++) {
			order.add(new int[] { refRanges.get(idx)[0], refRanges.get(idx)[1], idx });
		}
		order.sort(Comparator.comparingInt(a -> a[0]));

		StringBuilder finalText = new StringBuilder();
		List<StyleRange> finalRefStyles = new ArrayList<>();
		List<int[]> deltas = new ArrayList<>(); // {rawPosAfterSplice, cumulativeDelta}
		int cumulativeDelta = 0;
		int rawPos = 0;
		for (int[] o : order) {
			int start = o[0];
			int end = o[1];
			String uri = refUris.get(o[2]);
			finalText.append(rawPlain, rawPos, start);

			String cachedText = rawPlain.substring(start, end);
			EObject resolved = resolveUri(uri);
			boolean broken = resolved == null;
			String displayText = cachedText;
			if (resolved instanceof NamedElement) {
				String liveName = ((NamedElement) resolved).getName();
				if (liveName != null && !liveName.isEmpty()) {
					displayText = liveName;
				}
			}

			int finalStart = finalText.length();
			finalText.append(displayText);
			int finalEnd = finalText.length();
			finalRefStyles.add(newReferenceStyleRange(finalStart, finalEnd - finalStart, uri, broken));

			rawPos = end;
			cumulativeDelta += (finalEnd - finalStart) - (end - start);
			deltas.add(new int[] { end, cumulativeDelta });
		}
		finalText.append(rawPlain, rawPos, rawPlain.length());

		int finalLength = finalText.length();
		boolean[] bold = new boolean[finalLength];
		boolean[] italic = new boolean[finalLength];
		markRemapped(boldRanges, deltas, bold);
		markRemapped(italicRanges, deltas, italic);

		styledText.setText(finalText.toString());
		List<StyleRange> all = new ArrayList<>(Arrays.asList(mergeStyleArrays(bold, italic)));
		all.addAll(finalRefStyles);
		all.sort(Comparator.comparingInt(r -> r.start));
		styledText.setStyleRanges(all.toArray(new StyleRange[0]));
	}

	private static void markRemapped(List<int[]> rawRanges, List<int[]> deltas, boolean[] target) {
		for (int[] r : rawRanges) {
			int start = remapOffset(r[0], deltas);
			int end = remapOffset(r[1], deltas);
			for (int k = start; k < end && k < target.length; k++) {
				target[k] = true;
			}
		}
	}

	private static int remapOffset(int rawOffset, List<int[]> deltas) {
		int delta = 0;
		for (int[] d : deltas) {
			if (d[0] <= rawOffset) {
				delta = d[1];
			} else {
				break;
			}
		}
		return rawOffset + delta;
	}

	private StyleRange newReferenceStyleRange(int start, int length, String uri, boolean broken) {
		StyleRange range = new StyleRange();
		range.start = start;
		range.length = length;
		range.underline = true;
		range.strikeout = broken;
		range.foreground = styledText.getDisplay().getSystemColor(broken ? SWT.COLOR_RED : SWT.COLOR_LINK_FOREGROUND);
		range.data = new ReferenceData(uri);
		return range;
	}

	private boolean[] computeStyleArray(int styleBit) {
		int n = styledText.getCharCount();
		boolean[] result = new boolean[n];
		for (StyleRange range : styledText.getStyleRanges()) {
			if ((range.fontStyle & styleBit) != 0) {
				int end = Math.min(range.start + range.length, n);
				for (int i = Math.max(range.start, 0); i < end; i++) {
					result[i] = true;
				}
			}
		}
		return result;
	}

	private static StyleRange newRange(int start, int length, int fontStyle) {
		StyleRange range = new StyleRange();
		range.start = start;
		range.length = length;
		range.fontStyle = fontStyle;
		return range;
	}

	/** Merges parallel bold/italic boolean arrays into minimal, non-overlapping {@link StyleRange}s. */
	private static StyleRange[] mergeStyleArrays(boolean[] bold, boolean[] italic) {
		int n = bold.length;
		List<StyleRange> ranges = new ArrayList<>();
		int runStart = -1;
		boolean runBold = false;
		boolean runItalic = false;
		for (int i = 0; i <= n; i++) {
			boolean b = i < n && bold[i];
			boolean it = i < n && italic[i];
			if (i == n || b != runBold || it != runItalic) {
				if (runStart >= 0 && (runBold || runItalic)) {
					ranges.add(newRange(runStart, i - runStart, (runBold ? SWT.BOLD : SWT.NORMAL) | (runItalic ? SWT.ITALIC : SWT.NORMAL)));
				}
				runStart = i;
				runBold = b;
				runItalic = it;
			}
		}
		return ranges.toArray(new StyleRange[0]);
	}

	/** Payload attached to a reference {@link StyleRange} via {@code StyleRange.data}. */
	private static final class ReferenceData {

		final String uri;

		ReferenceData(String uri) {
			this.uri = uri;
		}
	}
}
