// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.timing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gmf.runtime.diagram.core.util.ViewUtil;
import org.eclipse.gmf.runtime.notation.Connector;
import org.eclipse.gmf.runtime.notation.DecorationNode;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.gmf.runtime.notation.Edge;
import org.eclipse.gmf.runtime.notation.IdentityAnchor;
import org.eclipse.gmf.runtime.notation.Location;
import org.eclipse.gmf.runtime.notation.Node;
import org.eclipse.gmf.runtime.notation.NotationFactory;
import org.eclipse.gmf.runtime.notation.RelativeBendpoints;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.gmf.runtime.notation.datatype.RelativeBendpoint;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.DurationInterval;
import org.eclipse.uml2.uml.DurationConstraint;
import org.eclipse.uml2.uml.ExecutionSpecification;
import org.eclipse.uml2.uml.Interaction;
import org.eclipse.uml2.uml.Lifeline;
import org.eclipse.uml2.uml.Message;
import org.eclipse.uml2.uml.OccurrenceSpecification;
import org.eclipse.uml2.uml.UMLFactory;

/**
 * A real DurationConstraint between two events of a Sequence Diagram, drawn by
 * Papyrus' own duration link (the double arrow between two messages), created
 * without the palette tool - which creates nothing in this Papyrus version.
 *
 * <p>
 * Two parts are created, mirroring what the palette tool would do:
 * <ul>
 * <li><b>Model:</b> a {@link DurationConstraint} in {@code Interaction.ownedRule}
 * whose {@code constrainedElement}s are the two events (start first, like
 * Papyrus' own edit helper), with a {@link DurationInterval} specification
 * (the only type UML2 accepts there).</li>
 * <li><b>Notation:</b> a {@link Connector} of type {@value #EDGE_TYPE} whose
 * source/target are the *views* of the two messages (or execution
 * specifications) and whose {@link IdentityAnchor}s ({@code start}/{@code end})
 * say which end of that message/execution is meant - Papyrus derives the event
 * from exactly that (see its {@code OccurrenceSpecificationUtil}).</li>
 * </ul>
 *
 * <p>
 * Operator and expression are stored as an EAnnotation on the DurationConstraint
 * itself (references are live), and a
 * {@link DurationLinkParser} makes the link's name label show
 * {@code {<operator> <expression>}}. The specification's name additionally
 * holds that text for people reading the model (it is refreshed on every edit,
 * not on renames of referenced elements - the diagram label is the live one).
 */
public final class DurationLinkSupport {

	/** EAnnotation source on the DurationConstraint. */
	// Stored in the model; the "statemachine.labels" prefix is kept so existing models stay readable.
	public static final String SOURCE = "com.eulynx.papyrus.statemachine.labels.durationLink"; //$NON-NLS-1$

	/** Notation types used by Papyrus' sequence diagram for a DurationConstraint link (see its UMLVisualIDRegistry). */
	public static final String EDGE_TYPE = "DurationConstraint_Edge"; //$NON-NLS-1$

	public static final String NAME_LABEL_TYPE = "DurationConstraint_NameLabel"; //$NON-NLS-1$

	public static final String STEREOTYPE_LABEL_TYPE = "DurationConstraint_StereotypeLabel"; //$NON-NLS-1$

	/** Anchor terminals Papyrus understands (its AnchorConstants): first/send/top end and last/receive/bottom end. */
	private static final String START_TERMINAL = "start"; //$NON-NLS-1$

	private static final String END_TERMINAL = "end"; //$NON-NLS-1$

	private DurationLinkSupport() {
	}

	/** One selectable end of a message or execution specification that is shown in the diagram. */
	public static final class Endpoint {

		public final String label;

		/** The Message edge view or the ExecutionSpecification node view the link will attach to. */
		public final View view;

		public final String terminal;

		public final OccurrenceSpecification event;

		final int order;

		Endpoint(String label, View view, String terminal, OccurrenceSpecification event, int order) {
			this.label = label;
			this.view = view;
			this.terminal = terminal;
			this.event = event;
			this.order = order;
		}
	}

	/**
	 * All message ends and execution specification starts/finishes that are
	 * shown in {@code diagram}, in the order of the Interaction (top to bottom).
	 */
	public static List<Endpoint> collectEndpoints(Diagram diagram, Interaction interaction) {
		Map<EObject, Integer> order = new HashMap<>();
		int index = 0;
		for (Iterator<EObject> it = interaction.eAllContents(); it.hasNext();) {
			order.put(it.next(), index++);
		}

		List<Endpoint> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (Object o : diagram.getEdges()) {
			if (!(o instanceof Edge)) {
				continue;
			}
			Edge edge = (Edge) o;
			if (!(edge.getElement() instanceof Message)) {
				continue;
			}
			Message message = (Message) edge.getElement();
			String name = displayName(message.getName());
			addEndpoint(result, seen, order, edge, START_TERMINAL, message.getSendEvent(), name + " – send", message); //$NON-NLS-1$
			addEndpoint(result, seen, order, edge, END_TERMINAL, message.getReceiveEvent(), name + " – receive", message); //$NON-NLS-1$
		}

		for (Iterator<EObject> it = diagram.eAllContents(); it.hasNext();) {
			EObject next = it.next();
			if (next instanceof Node && ((Node) next).getElement() instanceof ExecutionSpecification) {
				Node node = (Node) next;
				ExecutionSpecification execution = (ExecutionSpecification) node.getElement();
				String name = displayName(execution.getName());
				addEndpoint(result, seen, order, node, START_TERMINAL, execution.getStart(), name + " – execution start", execution); //$NON-NLS-1$
				addEndpoint(result, seen, order, node, END_TERMINAL, execution.getFinish(), name + " – execution end", execution); //$NON-NLS-1$
			}
		}

		result.sort(Comparator.<Endpoint>comparingInt(e -> e.order).thenComparing(e -> e.label));
		return result;
	}

	private static void addEndpoint(List<Endpoint> result, Set<String> seen, Map<EObject, Integer> order, View view, String terminal,
			Object event, String text, EObject owner) {
		if (!(event instanceof OccurrenceSpecification)) {
			return;
		}
		OccurrenceSpecification occurrence = (OccurrenceSpecification) event;
		// The same message can have several views; list each end once.
		if (!seen.add(System.identityHashCode(owner) + ":" + terminal)) { //$NON-NLS-1$
			return;
		}
		String lifeline = lifelineName(occurrence);
		String label = lifeline.isEmpty() ? text : text + " (" + lifeline + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		int position = order.getOrDefault(occurrence, Integer.MAX_VALUE);
		result.add(new Endpoint(label, view, terminal, occurrence, position));
	}

	private static String lifelineName(OccurrenceSpecification occurrence) {
		for (Lifeline lifeline : occurrence.getCovereds()) {
			if (lifeline.getName() != null && !lifeline.getName().isEmpty()) {
				return lifeline.getName();
			}
			if (lifeline.getRepresents() != null && lifeline.getRepresents().getName() != null) {
				return lifeline.getRepresents().getName();
			}
		}
		return ""; //$NON-NLS-1$
	}

	private static String displayName(String name) {
		return name == null || name.isEmpty() ? "(unnamed)" : name; //$NON-NLS-1$
	}

	/** Creates the DurationConstraint and its link in the diagram. Must run inside a write transaction. */
	public static DurationConstraint create(Diagram diagram, Interaction interaction, Endpoint start, Endpoint end, String operator,
			String expressionHtml) {
		DurationConstraint constraint = UMLFactory.eINSTANCE.createDurationConstraint();
		constraint.setName(uniqueName(interaction));
		interaction.getOwnedRules().add(constraint);
		constraint.getConstrainedElements().add(start.event);
		constraint.getConstrainedElements().add(end.event);
		apply(constraint, operator, expressionHtml);
		createLinkView(diagram, constraint, start, end);
		return constraint;
	}

	private static String uniqueName(Interaction interaction) {
		Set<String> used = new HashSet<>();
		interaction.getOwnedRules().forEach(rule -> used.add(rule.getName()));
		int number = 1;
		while (used.contains("DurationConstraint" + number)) { //$NON-NLS-1$
			number++;
		}
		return "DurationConstraint" + number; //$NON-NLS-1$
	}

	/** The same structure Papyrus' own view provider builds for a DurationConstraint link, plus source/target and anchors. */
	private static void createLinkView(Diagram diagram, DurationConstraint constraint, Endpoint start, Endpoint end) {
		Connector edge = NotationFactory.eINSTANCE.createConnector();
		edge.getStyles().add(NotationFactory.eINSTANCE.createFontStyle());
		RelativeBendpoints bendpoints = NotationFactory.eINSTANCE.createRelativeBendpoints();
		List<RelativeBendpoint> points = new ArrayList<>(2);
		points.add(new RelativeBendpoint());
		points.add(new RelativeBendpoint());
		bendpoints.setPoints(points);
		edge.setBendpoints(bendpoints);
		edge.setType(EDGE_TYPE);
		edge.setElement(constraint);

		edge.setSource(start.view);
		edge.setTarget(end.view);
		IdentityAnchor sourceAnchor = NotationFactory.eINSTANCE.createIdentityAnchor();
		sourceAnchor.setId(start.terminal);
		edge.setSourceAnchor(sourceAnchor);
		IdentityAnchor targetAnchor = NotationFactory.eINSTANCE.createIdentityAnchor();
		targetAnchor.setId(end.terminal);
		edge.setTargetAnchor(targetAnchor);

		// Attach to the diagram last, when the view is complete.
		ViewUtil.insertChildView(diagram, edge, ViewUtil.APPEND, true);

		Node nameLabel = createLabel(edge, NAME_LABEL_TYPE);
		Location nameLocation = NotationFactory.eINSTANCE.createLocation();
		nameLocation.setX(1);
		nameLocation.setY(-13);
		nameLabel.setLayoutConstraint(nameLocation);

		Node stereotypeLabel = createLabel(edge, STEREOTYPE_LABEL_TYPE);
		Location stereotypeLocation = NotationFactory.eINSTANCE.createLocation();
		stereotypeLocation.setX(1);
		stereotypeLocation.setY(-33);
		stereotypeLabel.setLayoutConstraint(stereotypeLocation);
		stereotypeLabel.setVisible(false);
	}

	private static Node createLabel(View owner, String type) {
		DecorationNode label = NotationFactory.eINSTANCE.createDecorationNode();
		label.setType(type);
		ViewUtil.insertChildView(owner, label, ViewUtil.APPEND, true);
		return label;
	}

	/**
	 * Stores operator/expression on the constraint (replacing the whole
	 * EAnnotation so the label's parser gets a notification, also on undo/redo)
	 * and refreshes the plain-text mirror in the Duration specification.
	 */
	public static void apply(DurationConstraint constraint, String operator, String expressionHtml) {
		EAnnotation old = constraint.getEAnnotation(SOURCE);
		if (old != null) {
			constraint.getEAnnotations().remove(old);
		}
		EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
		annotation.setSource(SOURCE);
		annotation.getDetails().put(DurationArrowSupport.KEY_OPERATOR, operator == null ? "" : operator); //$NON-NLS-1$
		annotation.getDetails().put(DurationArrowSupport.KEY_EXPRESSION, expressionHtml == null ? "" : expressionHtml); //$NON-NLS-1$
		constraint.getEAnnotations().add(annotation);

		// UML2 only accepts a DurationInterval as the specification of a
		// DurationConstraint. Its min/max would be separate Duration elements in
		// the package (Papyrus' own tool adds two per constraint); they are left
		// out on purpose - the value is the label, not a number. The label text
		// is kept as the interval's name so it is readable in the model.
		DurationInterval interval = constraint.getSpecification() instanceof DurationInterval
				? (DurationInterval) constraint.getSpecification()
				: UMLFactory.eINSTANCE.createDurationInterval();
		interval.setName(label(constraint));
		constraint.setSpecification(interval);
	}

	public static boolean hasData(EObject element) {
		return element instanceof DurationConstraint && ((DurationConstraint) element).getEAnnotation(SOURCE) != null;
	}

	public static String getOperator(DurationConstraint constraint) {
		return detail(constraint, DurationArrowSupport.KEY_OPERATOR);
	}

	public static String getExpressionHtml(DurationConstraint constraint) {
		return detail(constraint, DurationArrowSupport.KEY_EXPRESSION);
	}

	private static String detail(DurationConstraint constraint, String key) {
		EAnnotation annotation = constraint.getEAnnotation(SOURCE);
		if (annotation == null) {
			return ""; //$NON-NLS-1$
		}
		String value = annotation.getDetails().get(key);
		return value == null ? "" : value; //$NON-NLS-1$
	}

	/** {@code {<operator> <expression>}} with references resolved to their current names. */
	public static String label(DurationConstraint constraint) {
		return DurationArrowSupport.buildLabel(getOperator(constraint), getExpressionHtml(constraint), resourceSetOf(constraint));
	}

	/** The elements referenced from the expression (the label follows their renames). */
	public static List<EObject> referencedElements(DurationConstraint constraint) {
		return DurationArrowSupport.referencedElements(getExpressionHtml(constraint), resourceSetOf(constraint));
	}

	private static ResourceSet resourceSetOf(EObject element) {
		return element.eResource() != null ? element.eResource().getResourceSet() : null;
	}

	/** Edit operator/expression of an existing duration (one undo step). The two events stay as they are. */
	public static void edit(Shell shell, DurationConstraint constraint) {
		DurationArrowDialog dialog = new DurationArrowDialog(shell, constraint, getOperator(constraint), getExpressionHtml(constraint));
		if (dialog.open() != Window.OK) {
			return;
		}
		final String operator = dialog.getOperator();
		final String expression = dialog.getExpressionHtml();
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(constraint);
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit Duration") { //$NON-NLS-1$
			@Override
			protected void doExecute() {
				apply(constraint, operator, expression);
			}
		});
	}
}
