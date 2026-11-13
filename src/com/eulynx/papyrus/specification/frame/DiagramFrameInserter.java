package com.eulynx.papyrus.specification.frame;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.gmf.runtime.notation.Bounds;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.gmf.runtime.notation.NotationFactory;
import org.eclipse.gmf.runtime.notation.Shape;

/**
 * Builds and inserts a {@link DiagramFrameEditPart}-backed notation Shape
 * into a Diagram's children. Shared between the manual "add frame" command
 * and the framed-diagram creation commands, so the notation/EAnnotation
 * shape stays in one place.
 */
public final class DiagramFrameInserter {

	private static final int DEFAULT_X = 20;

	private static final int DEFAULT_Y = 20;

	private static final int DEFAULT_WIDTH = 300;

	private static final int DEFAULT_HEIGHT = 200;

	private DiagramFrameInserter() {
	}

	/**
	 * Inserts a frame with a fixed default size, suitable for a brand new
	 * (empty) diagram. Must be called from within an active write
	 * transaction.
	 *
	 * @param diagram
	 *            the diagram to add the frame to
	 * @param kindPrefix
	 *            the fixed title prefix, e.g. "uc" or "bdd"
	 */
	public static void insertFrame(Diagram diagram, String kindPrefix) {
		insertFrame(diagram, kindPrefix, DEFAULT_X, DEFAULT_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
	}

	/**
	 * Inserts a frame with an explicit position/size. Must be called from
	 * within an active write transaction.
	 */
	public static void insertFrame(Diagram diagram, String kindPrefix, int x, int y, int width, int height) {
		Shape shape = NotationFactory.eINSTANCE.createShape();
		shape.setType(DiagramFrameEditPart.VISUAL_ID);
		shape.setElement(null);

		Bounds bounds = NotationFactory.eINSTANCE.createBounds();
		bounds.setX(x);
		bounds.setY(y);
		bounds.setWidth(width);
		bounds.setHeight(height);
		shape.setLayoutConstraint(bounds);

		shape.getStyles().add(NotationFactory.eINSTANCE.createShapeStyle());

		EAnnotation kindAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
		kindAnnotation.setSource(DiagramFrameEditPart.KIND_ANNOTATION_SOURCE);
		kindAnnotation.getDetails().put(DiagramFrameEditPart.KIND_DETAIL_KEY, kindPrefix);
		shape.getEAnnotations().add(kindAnnotation);

		// Insert at the back (index 0) rather than appending, so the frame
		// renders behind existing diagram content instead of covering it.
		diagram.getPersistedChildren().add(0, shape);
	}
}
