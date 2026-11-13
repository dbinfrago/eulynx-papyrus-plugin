package com.eulynx.papyrus.specification.statemachine.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.eulynx.papyrus.specification.richtext.InsertReferenceDialog;
import com.eulynx.papyrus.specification.richtext.TextReferences;
import com.eulynx.papyrus.specification.richtext.TextReferences.Ref;
import com.eulynx.papyrus.specification.richtext.TextReferences.Value;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.NamedElement;

/**
 * Text field whose inserted references stay live ({@link TextReferences}): a
 * reference is shown blue and underlined, its URI sits on the style range.
 * "Insert Reference..." (called by the owner's button) inserts the element's
 * name as such a reference. When the text is read back, a reference whose
 * displayed text no longer equals the target's name is dropped and stays plain
 * text.
 */
public final class ReferenceText {

	private static final class ReferenceData {

		final String uri;

		ReferenceData(String uri) {
			this.uri = uri;
		}
	}

	private final StyledText styledText;

	private final EObject contextElement;

	public ReferenceText(Composite parent, int style, EObject contextElement) {
		this.styledText = new StyledText(parent, style | SWT.BORDER);
		this.contextElement = contextElement;
	}

	public StyledText getControl() {
		return styledText;
	}

	/** Sets the text and shows {@code refs} (invalid ones are ignored). */
	public void setContent(String text, List<Ref> refs) {
		styledText.setText(text == null ? "" : text); //$NON-NLS-1$
		List<StyleRange> ranges = new ArrayList<>();
		if (refs != null) {
			for (Ref ref : refs) {
				if (ref.start >= 0 && ref.start + ref.length <= styledText.getCharCount()) {
					ranges.add(newRange(ref.start, ref.length, ref.uri));
				}
			}
		}
		ranges.sort(Comparator.comparingInt(r -> r.start));
		styledText.setStyleRanges(ranges.toArray(new StyleRange[0]));
	}

	/**
	 * The current text with its still valid references - call while the widget
	 * exists. With {@code trim} surrounding whitespace is removed (and the
	 * reference positions shifted accordingly).
	 */
	public Value capture(boolean trim) {
		String raw = styledText.getText();
		int lead = 0;
		String text = raw;
		if (trim) {
			lead = raw.length() - raw.stripLeading().length();
			text = raw.strip();
		}
		ResourceSet resourceSet = contextElement.eResource() == null ? null : contextElement.eResource().getResourceSet();
		List<Ref> refs = new ArrayList<>();
		for (StyleRange range : styledText.getStyleRanges()) {
			if (!(range.data instanceof ReferenceData)) {
				continue;
			}
			int start = range.start - lead;
			if (start < 0 || start + range.length > text.length()) {
				continue;
			}
			String uri = ((ReferenceData) range.data).uri;
			NamedElement target = TextReferences.resolve(uri, resourceSet);
			String shown = text.substring(start, start + range.length);
			if (target != null && shown.equals(target.getName())) {
				refs.add(new Ref(start, range.length, uri));
			}
		}
		refs.sort(Comparator.comparingInt(r -> r.start));
		return new Value(text, refs);
	}

	/** Opens the element picker and inserts the picked element's name as a live reference. */
	public void insertReference(Shell shell) {
		InsertReferenceDialog dialog = new InsertReferenceDialog(shell, contextElement);
		if (dialog.open() != Window.OK || !(dialog.getSelectedElement() instanceof NamedElement)) {
			return;
		}
		NamedElement selected = (NamedElement) dialog.getSelectedElement();
		String name = selected.getName();
		if (name == null || name.isEmpty()) {
			return;
		}
		String uri = EcoreUtil.getURI(selected).toString();
		Point selection = styledText.getSelectionRange();
		int position = selection.x;
		styledText.replaceTextRange(position, selection.y, name);
		List<StyleRange> combined = new ArrayList<>(Arrays.asList(styledText.getStyleRanges()));
		combined.add(newRange(position, name.length(), uri));
		combined.sort(Comparator.comparingInt(r -> r.start));
		styledText.setStyleRanges(combined.toArray(new StyleRange[0]));
		styledText.setSelectionRange(position + name.length(), 0);
		styledText.setFocus();
	}

	private StyleRange newRange(int start, int length, String uri) {
		StyleRange range = new StyleRange();
		range.start = start;
		range.length = length;
		range.foreground = styledText.getDisplay().getSystemColor(SWT.COLOR_BLUE);
		range.underline = true;
		range.data = new ReferenceData(uri);
		return range;
	}
}
