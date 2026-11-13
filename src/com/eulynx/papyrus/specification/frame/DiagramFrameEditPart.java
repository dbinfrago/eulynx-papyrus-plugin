package com.eulynx.papyrus.specification.frame;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.editpolicies.AbstractEditPolicy;
import org.eclipse.gmf.runtime.diagram.core.listener.DiagramEventBroker;
import org.eclipse.gmf.runtime.diagram.core.listener.NotificationListener;
import org.eclipse.gmf.runtime.diagram.ui.editparts.ShapeNodeEditPart;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.EditPolicyRoles;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.ResizableShapeEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.ViewComponentEditPolicy;
import org.eclipse.gmf.runtime.gef.ui.figures.NodeFigure;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.uml2.uml.Stereotype;

/**
 * Non-semantic decoration node: a UML/SysML-style diagram frame (border +
 * pentagon name tag). Its notation View has no UML model element
 * (View#getElement() == null), so it behaves like GMF's own "Note" node
 * (org.eclipse.gmf.runtime.diagram.ui.editparts.NoteEditPart): no semantic
 * edit policy, deletion handled purely at the view level.
 *
 * The pentagon title text is live-bound to the owning Diagram's name (not to
 * this node's own view, which has none), formatted as
 * "&lt;kind&gt; [&lt;owner type&gt;] &lt;diagram name&gt;", e.g.
 * "bdd [Package] MyDiagram". The "kind" prefix (uc/bdd/...) is fixed per
 * instance and stored as an EAnnotation on this node's own view, since
 * different diagram kinds share this same node type.
 */
public class DiagramFrameEditPart extends ShapeNodeEditPart {

	/** Notation View#getType() value identifying this node kind. */
	public static final String VISUAL_ID = "PapyrusDiagramFrame"; //$NON-NLS-1$

	/** EAnnotation source used to persist the fixed diagram-kind prefix (e.g. "uc", "bdd"). */
	// Stored in the model; the "statemachine.labels" prefix is kept so existing models stay readable.
	public static final String KIND_ANNOTATION_SOURCE = "com.eulynx.papyrus.statemachine.labels.frameKind"; //$NON-NLS-1$

	/** EAnnotation detail key holding the kind prefix string. */
	public static final String KIND_DETAIL_KEY = "kind"; //$NON-NLS-1$

	private final NotificationListener diagramNameListener = new NotificationListener() {
		@Override
		public void notifyChanged(Notification notification) {
			refreshTitle();
		}
	};

	private Diagram listenedDiagram;

	public DiagramFrameEditPart(View view) {
		super(view);
	}

	@Override
	protected NodeFigure createNodeFigure() {
		return new DiagramFrameFigure(200, 150);
	}

	public DiagramFrameFigure getPrimaryShape() {
		return (DiagramFrameFigure) getFigure();
	}

	@Override
	protected void createDefaultEditPolicies() {
		super.createDefaultEditPolicies();
		// No semantic (UML) element behind this view, so replace whatever
		// semantic edit policy was installed by default with a no-op one.
		installEditPolicy(EditPolicyRoles.SEMANTIC_ROLE, new AbstractEditPolicy() {
			// Intentionally empty: this node has nothing semantic to edit.
		});
		installEditPolicy(EditPolicy.COMPONENT_ROLE, new ViewComponentEditPolicy());
		// Install our own resize policy explicitly rather than relying on the
		// parent diagram's layout policy to hand us one via
		// getPrimaryDragEditPolicy() - the parent's layout policy may special
		// case unknown visual IDs unfavourably.
		installEditPolicy(EditPolicy.PRIMARY_DRAG_ROLE, new ResizableShapeEditPolicy());
		// This is a pure decoration, not a real container: it must never be
		// treated as one, or Papyrus's drag-drop/containment logic may block
		// shrinking it below the bounds of whatever it currently visually
		// overlaps (the ShapeEditPart default installs a ContainerEditPolicy
		// here, which is what almost certainly caused that exact symptom).
		removeEditPolicy(EditPolicy.CONTAINER_ROLE);
	}

	@Override
	public void activate() {
		super.activate();
		// The title depends on the owning Diagram's name, not on this node's
		// own (nonexistent) semantic element, so listen to the Diagram
		// separately via the DiagramEventBroker rather than relying on the
		// standard semantic-listener machinery (which only watches this
		// view's own element).
		Diagram diagram = ((View) getModel()).getDiagram();
		if (diagram != null) {
			listenedDiagram = diagram;
			DiagramEventBroker.getInstance(getEditingDomain()).addNotificationListener(diagram, diagramNameListener);
		}
		refreshTitle();
	}

	@Override
	public void deactivate() {
		if (listenedDiagram != null) {
			DiagramEventBroker.getInstance(getEditingDomain()).removeNotificationListener(listenedDiagram, diagramNameListener);
			listenedDiagram = null;
		}
		super.deactivate();
	}

	private void refreshTitle() {
		getPrimaryShape().setTitleText(computeTitleText());
	}

	private String computeTitleText() {
		Diagram diagram = ((View) getModel()).getDiagram();
		String name = diagram != null ? diagram.getName() : null;
		if (name == null || name.isEmpty()) {
			name = "(unnamed)"; //$NON-NLS-1$
		}

		StringBuilder text = new StringBuilder(getKindPrefix()).append(' ');
		EObject element = diagram != null ? diagram.getElement() : null;
		if (element != null) {
			text.append('[').append(elementTypeLabel(element)).append("] "); //$NON-NLS-1$
		}
		text.append(name);
		return text.toString();
	}

	/** SysML stereotype names that should be shown instead of the raw UML metaclass name. */
	private static final java.util.Set<String> PREFERRED_STEREOTYPE_NAMES = new java.util.HashSet<>(
			java.util.Arrays.asList("Block", "ConstraintBlock")); //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * "Block" (or "ConstraintBlock") if the owning element has that SysML
	 * stereotype applied - a Block Definition/Internal Block Diagram's owner
	 * is typically a plain UML {@code Class} stereotyped as a SysML Block, so
	 * showing the bare metaclass name would read "[Class]" instead of the
	 * "[Block]" the SysML standard expects. Elements often carry other,
	 * unrelated stereotypes too (e.g. a project-specific one), so this only
	 * ever looks for these two specific names rather than showing whichever
	 * stereotype happens to be applied first. Falls back to the raw UML
	 * metaclass name if neither is present.
	 */
	private String elementTypeLabel(EObject element) {
		if (element instanceof org.eclipse.uml2.uml.Element) {
			EList<Stereotype> stereotypes = ((org.eclipse.uml2.uml.Element) element).getAppliedStereotypes();
			for (Stereotype stereotype : stereotypes) {
				if (PREFERRED_STEREOTYPE_NAMES.contains(stereotype.getName())) {
					return stereotype.getName();
				}
			}
		}
		return element.eClass().getName();
	}

	private String getKindPrefix() {
		EAnnotation annotation = ((View) getModel()).getEAnnotation(KIND_ANNOTATION_SOURCE);
		if (annotation != null) {
			String kind = annotation.getDetails().get(KIND_DETAIL_KEY);
			if (kind != null && !kind.isEmpty()) {
				return kind;
			}
		}
		return "frame"; //$NON-NLS-1$
	}
}
