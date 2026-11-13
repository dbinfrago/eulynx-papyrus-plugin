package com.eulynx.papyrus.specification.statemachine.width;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.draw2d.FigureUtilities;
import org.eclipse.draw2d.IFigure;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.edit.part.CustomTransitionGuardEditPart;

/**
 * Transition guard/trigger/effect label that can be wrapped to a configured pixel
 * width ("Set Label Width..."). The width is kept in an annotation on the label's
 * view. When it is set, plain {@code \n} characters are inserted into the
 * <i>displayed</i> string at token boundaries (whitespace, {@code &&}, {@code ||},
 * {@code ==}, {@code !=}, single brackets/dots/commas - never inside an
 * identifier); the stored guard/effect text is not touched.
 */
public class WidthAwareTransitionGuardEditPart extends CustomTransitionGuardEditPart {

	/** EAnnotation source used to persist a per-label width, set by {@link SetLabelWidthHandler}. */
	// Stored in the model; the "statemachine.labels" prefix is kept so existing models stay readable.
	public static final String ANNOTATION_SOURCE = "com.eulynx.papyrus.statemachine.labels.labelWidth"; //$NON-NLS-1$

	/** EAnnotation detail key holding the width, as a plain integer string (pixels). */
	public static final String WIDTH_KEY = "width"; //$NON-NLS-1$

	public WidthAwareTransitionGuardEditPart(View view) {
		super(view);
	}

	@Override
	protected void setLabelTextHelper(IFigure figure, String text) {
		int width = readWidth();
		if (width <= 0 || text == null || text.isEmpty()) {
			super.setLabelTextHelper(figure, text);
			return;
		}
		super.setLabelTextHelper(figure, wrap(text, width, figure));
	}

	private int readWidth() {
		Object model = getModel();
		if (!(model instanceof View)) {
			return -1;
		}
		EAnnotation annotation = ((View) model).getEAnnotation(ANNOTATION_SOURCE);
		if (annotation == null) {
			return -1;
		}
		String value = annotation.getDetails().get(WIDTH_KEY);
		if (value == null) {
			return -1;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Wraps each existing line of {@code text} independently, so an already-multi-line body (e.g. an effect with several statements) keeps its real line breaks. */
	private String wrap(String text, int maxWidth, IFigure figure) {
		StringBuilder result = new StringBuilder();
		String[] paragraphs = text.split("\n", -1); //$NON-NLS-1$
		for (int i = 0; i < paragraphs.length; i++) {
			if (i > 0) {
				result.append('\n');
			}
			result.append(wrapParagraph(paragraphs[i], maxWidth, figure));
		}
		return result.toString();
	}

	/** Whitespace runs, the {@code &&}/{@code ||}/{@code ==}/{@code !=} operators, single {@code (}/{@code )}/{@code .}/{@code ,} characters, or otherwise a run of "word" characters - see {@link #wrapParagraph}. */
	private static final Pattern TOKEN_PATTERN = Pattern
			.compile("\\s+|&&|\\|\\||==|!=|:=|<=|>=|[().,]|[^\\s().,&|=!]+|."); //$NON-NLS-1$

	/**
	 * Splitting only on plain spaces wrapped badly: a guard/effect expression is
	 * often one very long space-free run (e.g. {@code
	 * D108in_Detection_Element_Status.equals("Detection}) that, being a single
	 * "word", could never be broken - it just became its own over-wide line
	 * regardless of the requested width, while short neighbouring words got stranded
	 * alone on their own lines too. This tokenizes at the same boundaries a human
	 * would naturally break Java-ish code at (see {@link #TOKEN_PATTERN}) so the
	 * packer has far more places to actually fit content onto one line. Breaking
	 * mid-expression like this is only ever a *display* choice (the result is never
	 * written back to the model), so it's fine even though it wouldn't be acceptable
	 * inside the real, compiled/synced text.
	 */
	private String wrapParagraph(String paragraph, int maxWidth, IFigure figure) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = TOKEN_PATTERN.matcher(paragraph);
		while (matcher.find()) {
			tokens.add(matcher.group());
		}

		StringBuilder result = new StringBuilder();
		StringBuilder line = new StringBuilder();
		for (String token : tokens) {
			boolean isWhitespace = token.isBlank();
			if (line.length() == 0 && isWhitespace) {
				continue; // never start a wrapped line with leading whitespace
			}
			String candidate = line + token;
			if (line.length() > 0 && !isWhitespace && FigureUtilities.getTextWidth(candidate, figure.getFont()) > maxWidth) {
				appendLine(result, line.toString());
				line = new StringBuilder(token);
			} else {
				line.append(token);
			}
		}
		appendLine(result, line.toString());
		return result.toString();
	}

	private void appendLine(StringBuilder result, String line) {
		if (result.length() > 0) {
			result.append('\n');
		}
		result.append(line.stripTrailing());
	}
}
