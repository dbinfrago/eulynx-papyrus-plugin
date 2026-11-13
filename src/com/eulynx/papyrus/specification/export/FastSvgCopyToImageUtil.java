// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.export;

import java.io.OutputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gmf.runtime.diagram.ui.editparts.DiagramEditPart;
import org.eclipse.gmf.runtime.diagram.ui.image.ImageFileFormat;
import org.eclipse.gmf.runtime.diagram.ui.render.clipboard.DiagramGenerator;
import org.eclipse.gmf.runtime.diagram.ui.render.clipboard.DiagramSVGGenerator;
import org.eclipse.gmf.runtime.diagram.ui.render.util.CopyToImageUtil;
import org.eclipse.jface.resource.ImageDescriptor;
import org.w3c.dom.Element;

/**
 * {@link CopyToImageUtil} whose SVG export skips a pointless, expensive step.
 * GMF's own {@code DiagramSVGGenerator.getImageDescriptor()} serializes the
 * finished SVG DOM to bytes and then immediately <b>rasterizes it again</b> with
 * Batik ({@code RenderedImageFactory}) just to hand back an image descriptor
 * that {@code copyToImage(...)} never uses when writing an SVG file - the file
 * itself is written afterwards from the DOM via {@code stream(...)}. This
 * subclass keeps only the DOM root and the view box and writes them out exactly
 * as the original {@code stream(...)} does, so the resulting SVG file is the
 * same, minus the wasted serialize-and-rasterize pass. Everything but SVG goes
 * through the normal {@link CopyToImageUtil} behaviour.
 */
public class FastSvgCopyToImageUtil extends CopyToImageUtil {

	@Override
	protected DiagramGenerator getDiagramGenerator(DiagramEditPart diagramEP, ImageFileFormat format) {
		if (ImageFileFormat.SVG.equals(format)) {
			return new FastSvgGenerator(diagramEP);
		}
		return super.getDiagramGenerator(diagramEP, format);
	}

	private static final class FastSvgGenerator extends DiagramSVGGenerator {

		private Element root;

		private Rectangle viewBox;

		FastSvgGenerator(DiagramEditPart diagramEditPart) {
			super(diagramEditPart);
		}

		@Override
		protected Graphics setUpGraphics(int width, int height) {
			viewBox = new Rectangle(0, 0, width, height);
			return super.setUpGraphics(width, height);
		}

		@Override
		protected ImageDescriptor getImageDescriptor(Graphics g) {
			try {
				// GraphicsSVG lives in an unexported internal package, hence reflection.
				root = (Element) g.getClass().getMethod("getRoot").invoke(g); //$NON-NLS-1$
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Cannot read the SVG root element: " + e.getMessage(), e); //$NON-NLS-1$
			}
			return null;
		}

		@Override
		public void stream(OutputStream outputStream) {
			try {
				root.setAttributeNS(null, "viewBox", viewBox.x + " " + viewBox.y + " " + viewBox.width + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						+ viewBox.height);
				Transformer transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.METHOD, "xml"); //$NON-NLS-1$
				transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
				transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //$NON-NLS-1$
				transformer.transform(new DOMSource(root), new StreamResult(outputStream));
			} catch (Exception e) {
				throw new IllegalStateException("Cannot write the SVG: " + e.getMessage(), e); //$NON-NLS-1$
			}
		}
	}
}
