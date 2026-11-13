// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.export;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.gmf.runtime.diagram.core.preferences.PreferencesHint;
import org.eclipse.gmf.runtime.diagram.ui.image.ImageFileFormat;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.diagram.ui.render.util.CopyToImageUtil;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Interaction;
import org.eclipse.uml2.uml.UseCase;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Export All Diagrams..." command: renders every diagram of the current model
 * (not only the open one) as an SVG file into a folder, by default
 * {@code <repo root>/sync/diagrams}. Diagrams of libraries and standard profiles
 * that are loaded alongside the project are skipped.
 * <p>
 * A Sequence Diagram whose {@code Interaction} is owned by a {@code UseCase}
 * (see {@link #targetDirFor(Diagram, File)}) is written into a subfolder named
 * after that UseCase; every other diagram goes into the top-level folder. File
 * names are the diagram names, sanitized for the file system and made unique.
 * <p>
 * Rendering is done by GMF's public {@link CopyToImageUtil} (via
 * {@code FastSvgCopyToImageUtil}), the same class Papyrus uses for "Save as
 * Image"; diagrams that are not open are rendered off-screen.
 */
public class ExportAllDiagramsHandler extends AbstractDiagramHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		Diagram currentDiagram = part == null ? null : part.getDiagram();
		if (currentDiagram == null) {
			MessageDialog.openWarning(shell, "Export All Diagrams", //$NON-NLS-1$
					"Open a diagram first, so the command knows which model's diagrams to export."); //$NON-NLS-1$
			return null;
		}

		List<Diagram> diagrams = collectDiagrams(currentDiagram);
		if (diagrams.isEmpty()) {
			MessageDialog.openWarning(shell, "Export All Diagrams", "No diagrams found in this model."); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}

		DirectoryDialog dialog = new DirectoryDialog(shell);
		dialog.setText("Export All Diagrams"); //$NON-NLS-1$
		dialog.setMessage(
				"Choose the folder to export every diagram into as PNG + SVG (e.g. the eulynx-model repo's sync/diagrams folder):"); //$NON-NLS-1$
		String defaultFolder = guessDefaultOutputFolder(currentDiagram);
		// The native folder-browse dialog silently ignores setFilterPath(...) and falls back to its own
		// last-visited folder when the given path doesn't exist yet - create it up front so our guessed
		// default (typically "<repo root>/sync/diagrams", which usually doesn't exist on a fresh checkout)
		// actually gets shown instead of some unrelated folder.
		new File(defaultFolder).mkdirs();
		dialog.setFilterPath(defaultFolder);
		String chosen = dialog.open();
		if (chosen == null) {
			return null;
		}

		File outputDir = new File(chosen);
		outputDir.mkdirs();

		CopyToImageUtil util = new FastSvgCopyToImageUtil();
		Map<String, Integer> usedNames = new LinkedHashMap<>();
		List<String> failures = new ArrayList<>();
		int exported = 0;
		for (Diagram diagram : diagrams) {
			String baseName = uniqueFileName(sanitize(diagram.getName()), usedNames);
			File targetDir = targetDirFor(diagram, outputDir);
			targetDir.mkdirs();
			try {
				// SVG only
				IPath svgPath = new Path(new File(targetDir, baseName + ".svg").getAbsolutePath()); //$NON-NLS-1$
				util.copyToImage(diagram, svgPath, ImageFileFormat.SVG, new NullProgressMonitor(),
						PreferencesHint.USE_DEFAULTS);
				exported++;
			} catch (CoreException e) {
				failures.add(diagram.getName() + ": " + e.getMessage()); //$NON-NLS-1$
			}
		}

		StringBuilder message = new StringBuilder();
		message.append("Exported ").append(exported).append(" of ").append(diagrams.size()) //$NON-NLS-1$ //$NON-NLS-2$
				.append(" diagrams to:\n").append(outputDir.getAbsolutePath()); //$NON-NLS-1$
		if (!failures.isEmpty()) {
			message.append("\n\nFailed:\n"); //$NON-NLS-1$
			for (String failure : failures) {
				message.append(failure).append('\n');
			}
		}
		MessageDialog.openInformation(shell, "Export All Diagrams", message.toString()); //$NON-NLS-1$
		return null;
	}

	/**
	 * Every {@link Diagram} view across every {@code .notation} resource in the
	 * same ResourceSet that belongs to the <b>same project</b> as
	 * {@code anyDiagram} - excludes diagrams from separately-loaded referenced
	 * libraries/profiles (e.g. the standard SysML/UML profile's own
	 * documentation diagrams), which would otherwise flood the export since a
	 * Papyrus model's ResourceSet always has these loaded alongside the actual
	 * project content. Falls back to no filtering (every diagram in the
	 * ResourceSet) if the current diagram's own project can't be resolved,
	 * rather than risk silently exporting nothing.
	 */
	private static List<Diagram> collectDiagrams(Diagram anyDiagram) {
		IProject referenceProject = resolveProject(anyDiagram.eResource());
		List<Diagram> result = new ArrayList<>();
		ResourceSet resourceSet = anyDiagram.eResource().getResourceSet();
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".notation")) { //$NON-NLS-1$
				continue;
			}
			if (referenceProject != null && !referenceProject.equals(resolveProject(resource))) {
				continue;
			}
			for (EObject content : resource.getContents()) {
				if (content instanceof Diagram) {
					result.add((Diagram) content);
				}
			}
		}
		return result;
	}

	/**
	 * A Sequence Diagram whose {@code Interaction} is directly owned by a
	 * {@code UseCase} (via UML's {@code ownedBehavior}/{@code
	 * classifierBehavior} - the team's existing modeling convention, mirroring
	 * how Capella nests Scenarios inside a Capability via {@code
	 * ownedScenarios}) exports into a subfolder named after that UseCase,
	 * instead of directly into {@code outputDir} - groups an SD with the
	 * other diagrams belonging to the same Use Case instead of leaving every
	 * diagram in one flat folder. Every other diagram (BDD/IBD/STM/etc., or
	 * an SD not owned by a UseCase this way) is unaffected and stays at the
	 * top level, exactly as before.
	 */
	private static File targetDirFor(Diagram diagram, File outputDir) {
		EObject element = diagram.getElement();
		if (element instanceof Interaction && element.eContainer() instanceof UseCase) {
			UseCase useCase = (UseCase) element.eContainer();
			return new File(outputDir, sanitize(useCase.getName()));
		}
		return outputDir;
	}

	/** The workspace {@link IProject} a resource's platform URI resolves to, or {@code null} if it can't be determined. */
	private static IProject resolveProject(Resource resource) {
		try {
			URI uri = resource.getURI();
			if (uri.isPlatformResource()) {
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(uri.toPlatformString(true)));
				IProject project = file.getProject();
				if (project != null && project.exists()) {
					return project;
				}
			}
		} catch (RuntimeException e) {
			// fall through - caller treats null as "unresolvable"
		}
		return null;
	}

	/** Defaults to "<repo root>/sync/diagrams" (the project's own parent folder) when resolvable, else the user's home folder. */
	private static String guessDefaultOutputFolder(Diagram diagram) {
		IProject project = resolveProject(diagram.eResource());
		if (project != null && project.getLocation() != null) {
			File repoRoot = project.getLocation().toFile().getParentFile();
			if (repoRoot != null) {
				return new File(new File(repoRoot, "sync"), "diagrams").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return project.getLocation().toOSString();
		}
		return System.getProperty("user.home"); //$NON-NLS-1$
	}

	private static String sanitize(String name) {
		if (name == null || name.isBlank()) {
			name = "Diagram"; //$NON-NLS-1$
		}
		return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String uniqueFileName(String baseName, Map<String, Integer> usedNames) {
		Integer count = usedNames.get(baseName);
		if (count == null) {
			usedNames.put(baseName, 1);
			return baseName;
		}
		usedNames.put(baseName, count + 1);
		return baseName + "_" + count; //$NON-NLS-1$
	}
}
