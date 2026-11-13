// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.export;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.papyrus.infra.nattable.manager.table.INattableModelManager;
import org.eclipse.papyrus.infra.nattable.model.nattable.Table;
import org.eclipse.papyrus.infra.nattable.utils.NattableModelManagerFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Export Tables..." command (Model Explorer): writes every Papyrus Table below
 * the selected Package(s) as an SVG file into {@code <repo root>/sync/tables}
 * (file name = sanitized Table name), where "Export Requirements..." picks it up
 * to embed it. Run it before "Export Requirements..." whenever a Table changed.
 * <p>
 * Cell layout and text are read from a NatTable built for the Table and drawn
 * as plain {@code <rect>}/{@code <text>} elements; fonts and colors beyond a
 * simple grey header are not reproduced.
 */
public class ExportTablesHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		List<Package> roots = resolveSelectedPackages(HandlerUtil.getCurrentStructuredSelection(event));
		if (roots.isEmpty()) {
			MessageDialog.openWarning(shell, "Export Tables", //$NON-NLS-1$
					"Select one or more Packages in the Model Explorer first."); //$NON-NLS-1$
			return null;
		}

		String defaultFolder = guessDefaultOutputFolder(resolveProject(roots.get(0).eResource()));

		List<Table> tables = new ArrayList<>();
		for (Package root : roots) {
			ResourceSet resourceSet = root.eResource().getResourceSet();
			collectTables(resourceSet, root, tables);
		}

		if (tables.isEmpty()) {
			MessageDialog.openInformation(shell, "Export Tables", //$NON-NLS-1$
					"No Tables found under the selected Package(s)."); //$NON-NLS-1$
			return null;
		}

		// No dialog: always <repo root>/sync/tables, where
		// ExportRequirementsHandler.resolveTableImageFile looks for them.
		String targetFolder = defaultFolder;

		List<String> failures = new ArrayList<>();
		int written = 0;
		for (Table table : tables) {
			try {
				exportOneTable(table, new File(targetFolder, sanitize(table.getName()) + ".svg")); //$NON-NLS-1$
				written++;
			} catch (Exception e) {
				failures.add(table.getName() + ": " + e.getMessage()); //$NON-NLS-1$
			}
		}

		StringBuilder message = new StringBuilder("Wrote " + written + " of " + tables.size() //$NON-NLS-1$ //$NON-NLS-2$
				+ " table(s) as SVG to:\n" + targetFolder //$NON-NLS-1$
				+ "\n\nNow run \"Export Requirements...\" to embed them."); //$NON-NLS-1$
		if (!failures.isEmpty()) {
			message.append("\n\nFailed:\n"); //$NON-NLS-1$
			for (String failure : failures) {
				message.append(failure).append('\n');
			}
		}
		MessageDialog.openInformation(shell, "Export Tables", message.toString()); //$NON-NLS-1$
		return null;
	}

	/**
	 * Papyrus's native export is a raster-only SWT path ({@code
	 * PapyrusImageExporter}/{@code ImageLoader}) with no SVG option, so there is
	 * nothing to switch on there: instead a NatTable of our own is built in a real,
	 * opened (off-screen, never-visible-to-the-user-for- long) Shell and every cell
	 * is read through the NatTable layer API - position, size, and the text its
	 * display converter produces - then drawn as plain {@code <rect>}/{@code <text>}
	 * elements. Only layout and text are reproduced, not Papyrus's per-cell
	 * fonts/colors beyond a simple grey header shading. As a side benefit there is
	 * no save dialog any more.
	 */
	private static void exportOneTable(Table table, File target) throws IOException {
		Display display = Display.getDefault();
		INattableModelManager manager = NattableModelManagerFactory.INSTANCE.createNatTableModelManager(table, null);
		Shell shell = new Shell(display, SWT.NO_TRIM);
		try {
			// Large enough that the viewport never clips.
			shell.setSize(3000, 3000);
			shell.setLayout(new FillLayout());
			NatTable natTable = manager.createNattable(shell, SWT.NONE, null);
			shell.setLocation(-10000, -10000);
			shell.open();
			while (display.readAndDispatch()) {
				// let NatTable lay itself out
			}

			IConfigRegistry registry = natTable.getConfigRegistry();
			StringBuilder cells = new StringBuilder();
			int width = 0;
			int height = 0;
			int clipId = 0;
			for (int row = 0; row < natTable.getRowCount(); row++) {
				for (int col = 0; col < natTable.getColumnCount(); col++) {
					ILayerCell cell = natTable.getCellByPosition(col, row);
					if (cell == null || cell.getOriginColumnPosition() != col || cell.getOriginRowPosition() != row) {
						continue;
					}
					Rectangle b = cell.getBounds();
					if (b.width <= 0 || b.height <= 0) {
						continue;
					}
					Object canonical = cell.getDataValue();
					IDisplayConverter converter = registry.getConfigAttribute(CellConfigAttributes.DISPLAY_CONVERTER,
							cell.getDisplayMode(), cell.getConfigLabels().getLabels());
					Object shown = converter == null ? canonical : converter.canonicalToDisplayValue(cell, registry, canonical);
					String text = shown == null ? "" : String.valueOf(shown); //$NON-NLS-1$

					boolean header = false;
					for (String label : cell.getConfigLabels().getLabels()) {
						String upper = label.toUpperCase();
						if (upper.contains("HEADER") || upper.contains("CORNER")) { //$NON-NLS-1$ //$NON-NLS-2$
							header = true;
						}
					}
					width = Math.max(width, b.x + b.width);
					height = Math.max(height, b.y + b.height);
					clipId++;
					cells.append("<clipPath id=\"c").append(clipId).append("\"><rect x=\"").append(b.x).append("\" y=\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(b.y).append("\" width=\"").append(b.width).append("\" height=\"").append(b.height) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append("\"/></clipPath>\n"); //$NON-NLS-1$
					cells.append("<rect x=\"").append(b.x).append("\" y=\"").append(b.y).append("\" width=\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(b.width).append("\" height=\"").append(b.height).append("\" fill=\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(header ? "#e6e6e6" : "#ffffff").append("\" stroke=\"#9a9a9a\"/>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					String[] lines = text.split("\\R", -1); //$NON-NLS-1$
					cells.append("<g clip-path=\"url(#c").append(clipId).append(")\" font-family=\"Arial, sans-serif\" font-size=\"12\"") //$NON-NLS-1$ //$NON-NLS-2$
							.append(header ? " font-weight=\"bold\"" : "").append(">\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					for (int i = 0; i < lines.length; i++) {
						cells.append("<text x=\"").append(b.x + 4).append("\" y=\"").append(b.y + 15 + i * 14) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								.append("\">").append(escapeXml(lines[i])).append("</text>\n"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					cells.append("</g>\n"); //$NON-NLS-1$
				}
			}
			if (width <= 0 || height <= 0) {
				throw new IOException("table has no visible cells (size " + width + "x" + height + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" //$NON-NLS-1$
					+ (width + 1) + "\" height=\"" + (height + 1) + "\" viewBox=\"0 0 " + (width + 1) + " " + (height + 1) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ "\">\n" + cells + "</svg>\n"; //$NON-NLS-1$ //$NON-NLS-2$
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), svg.getBytes(StandardCharsets.UTF_8));
		} finally {
			manager.dispose();
			shell.dispose();
		}
	}

	private static String escapeXml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	}

	private static String sanitize(String name) {
		return name == null ? "table" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/** Every {@link Table} found under {@code context} - the Package itself, plus every direct child element's own Table (Class/Signal/UseCase - matches {@code ExportRequirementsHandler.appendPackageContents}'s "at most one context level below a Package" pattern) - recursing into nested Packages. */
	private static void collectTables(ResourceSet resourceSet, Package pkg, List<Table> result) {
		result.addAll(findAllTables(resourceSet, pkg));
		for (PackageableElement element : pkg.getPackagedElements()) {
			if (element instanceof Package) {
				collectTables(resourceSet, (Package) element, result);
			} else {
				result.addAll(findAllTables(resourceSet, element));
			}
		}
	}

	/** Every {@link Table} view in {@code resourceSet} whose {@code context} is (by identity) {@code element} - independently duplicated from {@code ExportRequirementsHandler.findAllTables}. */
	private static List<Table> findAllTables(ResourceSet resourceSet, EObject element) {
		List<Table> result = new ArrayList<>();
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".notation")) { //$NON-NLS-1$
				continue;
			}
			for (EObject content : resource.getContents()) {
				if (content instanceof Table && ((Table) content).getContext() == element) {
					result.add((Table) content);
				}
			}
		}
		return result;
	}

	/** Every {@link Package} in {@code selection} - independently duplicated from {@code ExportRequirementsHandler.resolveSelectedPackages}. */
	private static List<Package> resolveSelectedPackages(IStructuredSelection selection) {
		List<Package> result = new ArrayList<>();
		for (Object selected : selection.toList()) {
			Package resolved = resolvePackage(selected);
			if (resolved != null) {
				result.add(resolved);
			}
		}
		return result;
	}

	private static Package resolvePackage(Object selected) {
		if (selected instanceof Package) {
			return (Package) selected;
		}
		if (selected instanceof IAdaptable) {
			Object adapted = ((IAdaptable) selected).getAdapter(Package.class);
			if (adapted instanceof Package) {
				return (Package) adapted;
			}
		}
		Object adapted = selected == null ? null : Platform.getAdapterManager().getAdapter(selected, Package.class);
		return adapted instanceof Package ? (Package) adapted : null;
	}

	/** The workspace {@link IProject} a resource's platform URI resolves to, or {@code null} if it can't be determined - independently duplicated from {@code ExportRequirementsHandler.resolveProject}. */
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

	/** Defaults to "<repo root>/sync/tables" when resolvable, else the user's home folder - same convention as {@code ExportRequirementsHandler.guessDefaultOutputFolder}'s "sync/requirements". */
	private static String guessDefaultOutputFolder(IProject project) {
		if (project != null && project.getLocation() != null) {
			File repoRoot = project.getLocation().toFile().getParentFile();
			if (repoRoot != null) {
				return new File(new File(repoRoot, "sync"), "tables").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return project.getLocation().toOSString();
		}
		return System.getProperty("user.home"); //$NON-NLS-1$
	}
}
