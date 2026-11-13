package com.eulynx.papyrus.specification.statemachine.region;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gmf.runtime.diagram.core.listener.DiagramEventBroker;
import org.eclipse.gmf.runtime.diagram.core.listener.NotificationListener;
import org.eclipse.gmf.runtime.diagram.ui.figures.ResizableCompartmentFigure;
import org.eclipse.gmf.runtime.notation.TitleStyle;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.papyrus.uml.diagram.statemachine.custom.edit.part.CustomRegionCompartmentEditPart;
import org.eclipse.uml2.uml.Region;
import org.eclipse.uml2.uml.State;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.UMLPackage;

/**
 * Zeigt als Titel des Region-Compartments den <b>Namen der Region</b> an,
 * statt des fest einkodierten Texts {@code "RegionCompartment"}.
 * <p>
 * Papyrus legt den Titel dieses Compartments in
 * {@code RegionCompartmentEditPart.getCompartmentName()} fest
 * ({@code Messages.RegionCompartmentEditPart_title}, immer
 * "RegionCompartment") und reicht ihn nur einmal beim Erzeugen des Figures
 * durch - der Name der zugehoerigen Region (das {@code Region_Shape}, in dem
 * dieses Compartment liegt) wird nirgends verwendet. Deshalb zeigte
 * "Show/Hide Compartments -&gt; Titel anzeigen" fuer jede Region denselben,
 * nichtssagenden Text. Hier wird der Titel stattdessen aus dem Namen der
 * Region gebildet und bei einer Umbenennung nachgezogen. Hat die Region
 * keinen Namen, bleibt es beim Papyrus-Standardtext.
 * <p>
 * Aendert nichts am Modell und nichts am Layout - nur der angezeigte
 * Titeltext des Figures.
 */
public class NamedRegionCompartmentEditPart extends CustomRegionCompartmentEditPart {

	private Region listenedRegion;

	private final NotificationListener regionNameListener = new NotificationListener() {
		@Override
		public void notifyChanged(Notification notification) {
			if (notification.getFeature() == UMLPackage.Literals.NAMED_ELEMENT__NAME) {
				updateTitle();
			}
		}
	};

	public NamedRegionCompartmentEditPart(View view) {
		super(view);
	}

	@Override
	public String getCompartmentName() {
		String name = regionName();
		if (name != null && !name.isBlank()) {
			return name;
		}
		return super.getCompartmentName();
	}

	@Override
	protected void refreshVisuals() {
		super.refreshVisuals();
		updateTitle();
	}

	/**
	 * Standardmaessig zeigt Papyrus den Titel nur, wenn jemand ihn per
	 * "Show/Hide Compartments" pro Region einzeln einschaltet - sonst gibt es
	 * gar keinen {@code TitleStyle} an der View und der Titel bleibt aus. Bei
	 * einem State/StateMachine mit <b>mehreren</b> Regionen sind die Namen aber
	 * genau das, was die Regionen unterscheidbar macht (wie in der
	 * DOORS/PDF-Darstellung). Deshalb: ohne expliziten {@code TitleStyle} wird
	 * der Name bei mehreren Regionen automatisch gezeigt. Sobald jemand den
	 * Titel selbst ein- oder ausschaltet, existiert ein {@code TitleStyle} und
	 * dessen Wert gilt unveraendert (Papyrus-Verhalten).
	 */
	@Override
	protected void refreshShowCompartmentTitle() {
		// Papyrus' normal behaviour first (explicit TitleStyle or CSS value).
		super.refreshShowCompartmentTitle();

		Object model = getModel();
		if (model instanceof View && !hasStoredTitleStyle((View) model) && hasSiblingRegions()) {
			setShowCompartmentTitle(true);
		}
	}

	/**
	 * Whether a {@code TitleStyle} is really <b>stored</b> on the view, i.e.
	 * somebody toggled the title by hand. {@code View.getStyle(TitleStyle)} is
	 * no usable check here: with the CSS engine active (every Papyrus diagram
	 * here) it never returns {@code null}, but a CSS stand-in object whose
	 * {@code showTitle} is simply {@code false}.
	 */
	private static boolean hasStoredTitleStyle(View view) {
		for (Object style : view.getStyles()) {
			if (style instanceof TitleStyle) {
				return true;
			}
		}
		return false;
	}

	/** {@code true} if the region's owning State/StateMachine has more than one region. */
	private boolean hasSiblingRegions() {
		Region region = region();
		if (region == null) {
			return false;
		}
		EObject owner = region.eContainer();
		if (owner instanceof State) {
			return ((State) owner).getRegions().size() > 1;
		}
		if (owner instanceof StateMachine) {
			return ((StateMachine) owner).getRegions().size() > 1;
		}
		return false;
	}

	@Override
	public void activate() {
		super.activate();
		Region region = region();
		if (region != null) {
			listenedRegion = region;
			DiagramEventBroker.getInstance(getEditingDomain()).addNotificationListener(region, regionNameListener);
		}
	}

	@Override
	public void deactivate() {
		if (listenedRegion != null) {
			DiagramEventBroker.getInstance(getEditingDomain()).removeNotificationListener(listenedRegion, regionNameListener);
			listenedRegion = null;
		}
		super.deactivate();
	}

	private void updateTitle() {
		if (getFigure() instanceof ResizableCompartmentFigure) {
			((ResizableCompartmentFigure) getFigure()).setTitle(getCompartmentName());
		}
	}

	private String regionName() {
		Region region = region();
		return region == null ? null : region.getName();
	}

	/** The region this compartment belongs to: the semantic element of the enclosing {@code Region_Shape}. */
	private Region region() {
		Object model = getModel();
		if (!(model instanceof View)) {
			return null;
		}
		View view = (View) model;
		if (view.getElement() instanceof Region) {
			return (Region) view.getElement();
		}
		EObject container = view.eContainer();
		if (container instanceof View && ((View) container).getElement() instanceof Region) {
			return (Region) ((View) container).getElement();
		}
		return null;
	}
}
