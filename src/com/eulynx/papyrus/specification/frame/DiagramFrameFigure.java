package com.eulynx.papyrus.specification.frame;

import org.eclipse.draw2d.AbstractBorder;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gmf.runtime.gef.ui.figures.DefaultSizeNodeFigure;

/**
 * Draws a UML/SysML-style diagram frame: a rectangle border with a
 * pentagon-shaped name tag in the top-left corner (e.g. "bdd [Package] Foo"),
 * modelled visually on Papyrus's Sequence Diagram frame
 * (org.eclipse.papyrus.uml.diagram.common.figure.node.InteractionRectangleFigure /
 * RoundedCompartmentFigure#getHeader()), but implemented independently since
 * this node has no compartments and no semantic element to hang labels off.
 */
public class DiagramFrameFigure extends DefaultSizeNodeFigure {

	/** Diagonal cut length of the pentagon's top-right corner. */
	private static final int CUT = 12;

	private static final int TAG_PADDING_H = 8;

	private static final int TAG_PADDING_V = 4;

	private final Label titleLabel = new Label();

	public DiagramFrameFigure(int defaultWidth, int defaultHeight) {
		super(defaultWidth, defaultHeight);
		setOpaque(false);
		setBorder(new PentagonFrameBorder());
		add(titleLabel);
	}

	public void setTitleText(String text) {
		titleLabel.setText(text);
		revalidate();
	}

	@Override
	public Dimension getMinimumSize(int wHint, int hHint) {
		// This node has no children whose bounds should ever constrain a
		// shrink, unlike the Sequence Diagram frame's compartment-driven
		// auto-grow. Keep the minimum small and fixed so the user can always
		// drag it back down after enlarging it.
		return new Dimension(40, 30);
	}

	@Override
	public void validate() {
		super.validate();
		Rectangle r = getBounds();
		Dimension pref = titleLabel.getPreferredSize();
		titleLabel.setBounds(new Rectangle(r.x + TAG_PADDING_H, r.y + TAG_PADDING_V, pref.width, pref.height));
	}

	private Dimension getTagSize() {
		Dimension pref = titleLabel.getPreferredSize();
		return new Dimension(pref.width + 2 * TAG_PADDING_H, pref.height + 2 * TAG_PADDING_V);
	}

	/**
	 * Draws the rectangle border plus the pentagon-shaped name tag, sized to
	 * fit the title label, in the top-left corner.
	 */
	private class PentagonFrameBorder extends AbstractBorder {

		@Override
		public Insets getInsets(IFigure figure) {
			return new Insets(1, 1, 1, 1);
		}

		@Override
		public void paint(IFigure figure, Graphics graphics, Insets insets) {
			Rectangle r = getPaintRectangle(figure, insets);
			r.shrink(1, 1);

			graphics.drawRectangle(r);

			Dimension tag = getTagSize();
			int tagWidth = Math.min(tag.width, r.width);
			int tagHeight = Math.min(tag.height, r.height);
			int cut = Math.min(CUT, Math.min(tagWidth, tagHeight));

			PointList pentagon = new PointList();
			pentagon.addPoint(r.x, r.y);
			pentagon.addPoint(r.x + tagWidth - cut, r.y);
			pentagon.addPoint(r.x + tagWidth, r.y + cut);
			pentagon.addPoint(r.x + tagWidth, r.y + tagHeight);
			pentagon.addPoint(r.x, r.y + tagHeight);
			pentagon.addPoint(r.x, r.y);

			graphics.drawPolyline(pentagon);
		}
	}
}
