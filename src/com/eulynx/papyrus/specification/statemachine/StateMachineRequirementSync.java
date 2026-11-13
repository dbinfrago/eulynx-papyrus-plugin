package com.eulynx.papyrus.specification.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Region;
import org.eclipse.uml2.uml.State;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.Vertex;

/**
 * Walks a {@link StateMachine} (recursively, including nested regions inside
 * composite states) and creates/updates one {@code «Requirement»} per state,
 * transition, and entry/exit/doActivity behavior, formatted via
 * {@link StateMachineTextFormatter} - so the result can be synced to Polarion
 * the same way any other Requirement already is.
 * <p>
 * Idempotent by construction, but identity and display name are kept
 * separate: each generated Requirement is linked to its source element via a
 * plain {@link Dependency} (client = the Requirement, supplier = the source
 * State/Transition/Behavior), kept out of sight in a "Traces" sub-package
 * (not interleaved with the Requirements themselves) - a repeated run looks
 * up "does a Requirement already have a Dependency to this exact source
 * element" rather than matching by name (this lookup works regardless of
 * which package the Dependency physically lives in - it's a computed
 * relationship, not a containment one), so the display name is free to be a
 * friendly {@code "<StateMachine name>_NNN"} (assigned once at creation and
 * left alone afterwards) instead of an internal identifier. This also gives
 * the team a genuine, navigable traceability link in the model, not just
 * internal bookkeeping.
 */
public class StateMachineRequirementSync {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	private int created;

	private int updated;

	public int getCreatedCount() {
		return created;
	}

	public int getUpdatedCount() {
		return updated;
	}

	/** The package generated Requirements for {@code stateMachine} should live in - reused if it already exists. */
	public Package resolveContainerPackage(StateMachine stateMachine) {
		String packageName = stateMachine.getName() + " - Requirements"; //$NON-NLS-1$
		Package parent = stateMachine.getNearestPackage();
		for (PackageableElement element : parent.getPackagedElements()) {
			if (element instanceof Package && packageName.equals(element.getName())) {
				return (Package) element;
			}
		}
		Package newPackage = UMLFactory.eINSTANCE.createPackage();
		newPackage.setName(packageName);
		parent.getPackagedElements().add(newPackage);
		return newPackage;
	}

	/** Sub-package (under the Requirements container) that the traceability {@link Dependency} elements live in, kept out of the way of the Requirements themselves - reused if it already exists. */
	private static Package resolveTracesPackage(Package container) {
		for (PackageableElement element : container.getPackagedElements()) {
			if (element instanceof Package && "Traces".equals(element.getName())) { //$NON-NLS-1$
				return (Package) element;
			}
		}
		Package tracesPackage = UMLFactory.eINSTANCE.createPackage();
		tracesPackage.setName("Traces"); //$NON-NLS-1$
		container.getPackagedElements().add(tracesPackage);
		return tracesPackage;
	}

	public void sync(StateMachine stateMachine, Package container) {
		created = 0;
		updated = 0;
		String namePrefix = stateMachine.getName() + "_"; //$NON-NLS-1$
		Pattern numberPattern = Pattern.compile(Pattern.quote(namePrefix) + "(\\d+)"); //$NON-NLS-1$
		Package tracesPackage = resolveTracesPackage(container);

		// Migrate any Dependency left directly in the container from before this
		// sub-package existed, so the tidy-up applies retroactively too.
		for (PackageableElement element : new ArrayList<>(container.getPackagedElements())) {
			if (element instanceof Dependency) {
				tracesPackage.getPackagedElements().add(element);
			}
		}

		Map<String, Class> existingBySource = new HashMap<>();
		int highestNumber = 0;
		for (PackageableElement element : container.getPackagedElements()) {
			if (!(element instanceof Class)) {
				continue;
			}
			Class umlClass = (Class) element;
			for (String sourceUri : sourceUris(umlClass)) {
				existingBySource.put(sourceUri, umlClass);
			}
			Matcher matcher = numberPattern.matcher(String.valueOf(umlClass.getName()));
			if (matcher.matches()) {
				highestNumber = Math.max(highestNumber, Integer.parseInt(matcher.group(1)));
			}
		}

		Counter counter = new Counter(highestNumber);
		for (Region region : stateMachine.getRegions()) {
			syncRegion(region, container, tracesPackage, existingBySource, namePrefix, counter);
		}
	}

	private void syncRegion(Region region, Package container, Package tracesPackage, Map<String, Class> existingBySource, String namePrefix, Counter counter) {
		for (Vertex vertex : region.getSubvertices()) {
			if (vertex.getName() != null && !vertex.getName().isEmpty()) {
				findOrCreate(vertex, StateMachineTextFormatter.textFor(vertex), container, tracesPackage, existingBySource, namePrefix, counter);
			}
			if (vertex instanceof State) {
				State state = (State) vertex;
				syncStateBehavior(state, "entry", state.getEntry(), container, tracesPackage, existingBySource, namePrefix, counter); //$NON-NLS-1$
				syncStateBehavior(state, "exit", state.getExit(), container, tracesPackage, existingBySource, namePrefix, counter); //$NON-NLS-1$
				syncStateBehavior(state, "do", state.getDoActivity(), container, tracesPackage, existingBySource, namePrefix, counter); //$NON-NLS-1$
				for (Region nested : state.getRegions()) {
					syncRegion(nested, container, tracesPackage, existingBySource, namePrefix, counter);
				}
			}
		}
		for (Transition transition : region.getTransitions()) {
			findOrCreate(transition, StateMachineTextFormatter.textFor(transition), container, tracesPackage, existingBySource, namePrefix, counter);
		}
	}

	private void syncStateBehavior(State state, String kindLabel, Behavior behavior, Package container, Package tracesPackage, Map<String, Class> existingBySource, String namePrefix, Counter counter) {
		if (behavior == null) {
			return;
		}
		findOrCreate(behavior, StateMachineTextFormatter.textForStateBehavior(state, kindLabel, behavior), container, tracesPackage, existingBySource, namePrefix, counter);
	}

	private void findOrCreate(EObject source, String text, Package container, Package tracesPackage, Map<String, Class> existingBySource, String namePrefix, Counter counter) {
		String sourceUri = EcoreUtil.getURI(source).toString();
		Class umlClass = existingBySource.get(sourceUri);
		if (umlClass == null) {
			umlClass = UMLFactory.eINSTANCE.createClass();
			umlClass.setName(namePrefix + String.format("%03d", counter.next())); //$NON-NLS-1$
			container.getPackagedElements().add(umlClass);

			Dependency dependency = UMLFactory.eINSTANCE.createDependency();
			dependency.getClients().add(umlClass);
			dependency.getSuppliers().add((NamedElement) source);
			tracesPackage.getPackagedElements().add(dependency);

			existingBySource.put(sourceUri, umlClass);
			created++;
		} else {
			updated++;
		}
		Stereotype stereotype = umlClass.getAppliedStereotype(REQUIREMENT_STEREOTYPE);
		if (stereotype == null) {
			stereotype = umlClass.getApplicableStereotype(REQUIREMENT_STEREOTYPE);
			umlClass.applyStereotype(stereotype);
		}
		umlClass.setValue(stereotype, "text", text); //$NON-NLS-1$
	}

	private static List<String> sourceUris(Class umlClass) {
		List<String> uris = new ArrayList<>();
		EList<Dependency> dependencies = umlClass.getClientDependencies();
		for (Dependency dependency : dependencies) {
			for (NamedElement supplier : dependency.getSuppliers()) {
				uris.add(EcoreUtil.getURI(supplier).toString());
			}
		}
		return uris;
	}

	private static final class Counter {

		private int value;

		Counter(int initial) {
			this.value = initial;
		}

		int next() {
			return ++value;
		}
	}
}
