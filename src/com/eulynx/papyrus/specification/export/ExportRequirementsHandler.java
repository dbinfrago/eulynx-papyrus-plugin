package com.eulynx.papyrus.specification.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gmf.runtime.diagram.core.preferences.PreferencesHint;
import org.eclipse.gmf.runtime.diagram.ui.editparts.DiagramEditPart;
import org.eclipse.gmf.runtime.diagram.ui.image.ImageFileFormat;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.papyrus.infra.core.resource.ModelSet;
import org.eclipse.papyrus.infra.core.sashwindows.di.service.IPageManager;
import org.eclipse.papyrus.infra.core.services.ServicesRegistry;
import org.eclipse.papyrus.infra.nattable.model.nattable.Table;
import org.eclipse.papyrus.infra.ui.editor.IMultiDiagramEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Feature;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Port;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Signal;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UseCase;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.eulynx.papyrus.specification.richtext.RichTextReferenceUtil;

/**
 * "Export Requirements..." command (Model Explorer): exports the content of the
 * selected top-level Package(s) as one ReqIF 1.2 package ({@code .reqifz}) with
 * one {@code SPECIFICATION} per selected Package, mirroring the Package
 * hierarchy as nested chapter headings. Every sub-Package becomes a heading,
 * whether or not it has content of its own.
 * <p>
 * Exported as work items:
 * <ul>
 * <li>Packages (heading), {@code «Requirement»} elements (name and text) and
 * {@link UseCase}s; a UseCase's Sequence Diagrams (its owned behaviors) are
 * nested below it as "Diagram" items;</li>
 * <li>every diagram and Table bound to a Package, a Block or a Signal context
 * (embedded as an image, a Table together with the first Comment of its
 * context as intro text); a Signal itself is not exported, only the Table under
 * it;</li>
 * <li>{@code Block}/{@code InterfaceBlock}-stereotyped Classes as "Block" items,
 * with their own diagrams, their FlowPorts (name, data type, description from
 * the Port's first Comment), their State Machine diagram and the
 * {@code DirectedFeature}-stereotyped features as "Signal" items;</li>
 * <li>{@code SignalAspect}-stereotyped Classes as "SignalAspect" items.</li>
 * </ul>
 * <p>
 * Requirement text is exported as plain text with bold preserved
 * (references become their current name). ReqIF identifiers derive from the
 * model elements, so a re-import updates instead of duplicating. Attribute
 * names use the Polarion system names ({@code ReqIF.Name}, {@code ReqIF.Text},
 * {@code ReqIF.ChapterName}). Table images are looked up in
 * {@code <repo root>/sync/tables} (see {@link ExportTablesHandler}).
 * <p>
 * The ReqIF helper methods are deliberately duplicated from
 * {@link ExportTransitionInformationHandler} instead of shared.
 */
public class ExportRequirementsHandler extends AbstractHandler {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	/** Matched by simple stereotype name only (see {@link #hasStereotype(Class, String)}) - the exact owning profile/qualified name isn't needed for this check, unlike {@link #REQUIREMENT_STEREOTYPE}. */
	private static final String INTERFACE_BLOCK_STEREOTYPE = "InterfaceBlock"; //$NON-NLS-1$

	/** The plain SysML {@code Blocks::Block} stereotype (e.g. "Subsystem - Level Crossing", "F_Observe_Detection_Element") - treated identically to {@link #INTERFACE_BLOCK_STEREOTYPE} since both become the same "Block" work item. */
	private static final String BLOCK_STEREOTYPE = "Block"; //$NON-NLS-1$

	private static final String DIRECTED_FEATURE_STEREOTYPE = "DirectedFeature"; //$NON-NLS-1$

	/** Matched by simple name, like the other custom EULYNX stereotypes above - each such Class becomes its own "SignalAspect" work item. */
	private static final String SIGNAL_ASPECT_STEREOTYPE = "SignalAspect"; //$NON-NLS-1$

	/**
	 * The {@code SignalAspect} stereotype's properties, names/order as in the
	 * profile. Index 0 ("Aspect meaning")
	 * becomes the work item's Description ({@code ReqIF.Text}); the rest become
	 * one custom string attribute each, named exactly like the property.
	 */
	private static final String[] SIGNAL_ASPECT_PROPERTIES = { "Aspect meaning", "Coding", "FTIA_310900", "NR_007000", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"SZ_007900", "DB_008000", "ÖBB_008100", "CFL_008200", "PR_008400", "SBB_008500", "SNCF_008700", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			"HZI_007800", "TRV_007400", "RFI_008300", "CPK_005110" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	private static final String REQIF_NS = "http://www.omg.org/spec/ReqIF/20110401/reqif.xsd"; //$NON-NLS-1$

	private static final String XHTML_NS = "http://www.w3.org/1999/xhtml"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		List<Package> roots = resolveSelectedPackages(HandlerUtil.getCurrentStructuredSelection(event));
		if (roots.isEmpty()) {
			MessageDialog.openWarning(shell, "Export Requirements", //$NON-NLS-1$
					"Select one or more Packages in the Model Explorer first."); //$NON-NLS-1$
			return null;
		}

		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setText("Export Requirements"); //$NON-NLS-1$
		dialog.setFilterExtensions(new String[] { "*.reqifz" }); //$NON-NLS-1$
		dialog.setFileName(roots.size() == 1 ? sanitize(roots.get(0).getName()) + ".reqifz" : "requirements.reqifz"); //$NON-NLS-1$ //$NON-NLS-2$
		IProject referenceProject = resolveProject(roots.get(0).eResource());
		String defaultFolder = guessDefaultOutputFolder(referenceProject);
		new File(defaultFolder).mkdirs();
		dialog.setFilterPath(defaultFolder);
		String chosen = dialog.open();
		if (chosen == null) {
			return null;
		}
		File outputFile = new File(chosen);
		if (outputFile.getParentFile() != null) {
			outputFile.getParentFile().mkdirs();
		}

		int[] counts = new int[6]; // [requirements, headings, useCases, blocks, signals, signalAspects]
		Map<String, byte[]> imageAttachments = new LinkedHashMap<>();
		OPENED_BY_EXPORT.clear();
		try {
			Document document = buildReqIfDocument(roots, counts, imageAttachments);
			writeReqifZ(document, imageAttachments, outputFile);
		} catch (Exception e) {
			MessageDialog.openError(shell, "Export Requirements", //$NON-NLS-1$
					"Failed to write ReqIF file: " + e.getMessage()); //$NON-NLS-1$
			return null;
		} finally {
			closePagesOpenedByExport();
		}

		MessageDialog.openInformation(shell, "Export Requirements", //$NON-NLS-1$
				"Exported " + roots.size() + " document(s) - " + counts[0] + " requirement(s), " + counts[2] //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ " use case(s), " + counts[3] + " block(s), " + counts[4] + " signal(s) and " + counts[5] //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							+ " signal aspect(s) under " //$NON-NLS-1$
						+ counts[1] + " heading(s) - to:\n" + outputFile.getAbsolutePath()); //$NON-NLS-1$
		return null;
	}

	/** Every {@link Package} in {@code selection}, in selection order - lets the user Ctrl-select several top-level Packages (e.g. "Functional requirements specification" and "Interface specification SCI-LC") and export them together into one {@code .reqif}, on request ("Alles in eins"), instead of one export run per root. */
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

	/**
	 * Builds the full ReqIF DOM: shared {@code DATATYPES}/{@code SPEC-TYPES}
	 * ("Heading" for Packages, "Requirement", "UseCase", "Diagram", "Block",
	 * "Signal") declared once, then one {@code SPECIFICATION} per entry in {@code
	 * roots}, each with its own recursive {@code SPEC-HIERARCHY} tree mirroring that
	 * root's Package structure. {@code counts} is filled with {@code
	 * [requirementCount, headingCount, useCaseCount, blockCount, signalCount]},
	 * summed across every root, as an out-parameter.
	 */
	private static Document buildReqIfDocument(List<Package> roots, int[] counts, Map<String, byte[]> imageAttachments)
			throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.newDocument();

		Element reqIf = doc.createElementNS(REQIF_NS, "REQ-IF"); //$NON-NLS-1$
		doc.appendChild(reqIf);

		Element header = doc.createElementNS(REQIF_NS, "THE-HEADER"); //$NON-NLS-1$
		reqIf.appendChild(header);
		Element headerInfo = doc.createElementNS(REQIF_NS, "REQ-IF-HEADER"); //$NON-NLS-1$
		headerInfo.setAttribute("IDENTIFIER", "HEADER-eulynx-requirements"); //$NON-NLS-1$ //$NON-NLS-2$
		header.appendChild(headerInfo);
		appendText(doc, headerInfo, "CREATION-TIME", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); //$NON-NLS-1$
		appendText(doc, headerInfo, "REQ-IF-TOOL-ID", "com.eulynx.papyrus.specification"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "REQ-IF-VERSION", "1.2"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "SOURCE-TOOL-ID", "Papyrus"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "TITLE", //$NON-NLS-1$
				roots.size() == 1 ? roots.get(0).getName() : "EULYNX Requirements"); //$NON-NLS-1$

		Element coreContent = doc.createElementNS(REQIF_NS, "CORE-CONTENT"); //$NON-NLS-1$
		reqIf.appendChild(coreContent);
		Element content = doc.createElementNS(REQIF_NS, "REQ-IF-CONTENT"); //$NON-NLS-1$
		coreContent.appendChild(content);

		Element datatypes = doc.createElementNS(REQIF_NS, "DATATYPES"); //$NON-NLS-1$
		content.appendChild(datatypes);
		Element nameType = doc.createElementNS(REQIF_NS, "DATATYPE-DEFINITION-STRING"); //$NON-NLS-1$
		nameType.setAttribute("IDENTIFIER", "DATATYPE-NAME"); //$NON-NLS-1$ //$NON-NLS-2$
		nameType.setAttribute("LONG-NAME", "Name"); //$NON-NLS-1$ //$NON-NLS-2$
		nameType.setAttribute("MAX-LENGTH", "256"); //$NON-NLS-1$ //$NON-NLS-2$
		datatypes.appendChild(nameType);
		Element longValueType = doc.createElementNS(REQIF_NS, "DATATYPE-DEFINITION-STRING"); //$NON-NLS-1$
		longValueType.setAttribute("IDENTIFIER", "DATATYPE-LONGVALUE"); //$NON-NLS-1$ //$NON-NLS-2$
		longValueType.setAttribute("LONG-NAME", "Value"); //$NON-NLS-1$ //$NON-NLS-2$
		longValueType.setAttribute("MAX-LENGTH", "4096"); //$NON-NLS-1$ //$NON-NLS-2$
		datatypes.appendChild(longValueType);
		Element textType = doc.createElementNS(REQIF_NS, "DATATYPE-DEFINITION-XHTML"); //$NON-NLS-1$
		textType.setAttribute("IDENTIFIER", "DATATYPE-TEXT"); //$NON-NLS-1$ //$NON-NLS-2$
		textType.setAttribute("LONG-NAME", "Text"); //$NON-NLS-1$ //$NON-NLS-2$
		datatypes.appendChild(textType);

		Element specTypes = doc.createElementNS(REQIF_NS, "SPEC-TYPES"); //$NON-NLS-1$
		content.appendChild(specTypes);

		Element headingType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		headingType.setAttribute("IDENTIFIER", "TYPE-HEADING"); //$NON-NLS-1$ //$NON-NLS-2$
		headingType.setAttribute("LONG-NAME", "Heading"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(headingType);
		Element headingAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		headingType.appendChild(headingAttributes);
		headingAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-HEADING-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.ChapterName", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element requirementType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		requirementType.setAttribute("IDENTIFIER", "TYPE-REQUIREMENT"); //$NON-NLS-1$ //$NON-NLS-2$
		requirementType.setAttribute("LONG-NAME", "Requirement"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(requirementType);
		Element requirementAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		requirementType.appendChild(requirementAttributes);
		requirementAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		requirementAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-TEXT", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element useCaseType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		useCaseType.setAttribute("IDENTIFIER", "TYPE-USECASE"); //$NON-NLS-1$ //$NON-NLS-2$
		useCaseType.setAttribute("LONG-NAME", "UseCase"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(useCaseType);
		Element useCaseAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		useCaseType.appendChild(useCaseAttributes);
		useCaseAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-USECASE-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		useCaseAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-USECASE-TEXT", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element diagramType =doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		diagramType.setAttribute("IDENTIFIER", "TYPE-DIAGRAM"); //$NON-NLS-1$ //$NON-NLS-2$
		diagramType.setAttribute("LONG-NAME", "Diagram"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(diagramType);
		Element diagramAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		diagramType.appendChild(diagramAttributes);
		diagramAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-DIAGRAM-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		diagramAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-DIAGRAM-IMAGE", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element blockType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		blockType.setAttribute("IDENTIFIER", "TYPE-BLOCK"); //$NON-NLS-1$ //$NON-NLS-2$
		blockType.setAttribute("LONG-NAME", "Block"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(blockType);
		Element blockAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		blockType.appendChild(blockAttributes);
		blockAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-BLOCK-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element signalType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		signalType.setAttribute("IDENTIFIER", "TYPE-SIGNAL"); //$NON-NLS-1$ //$NON-NLS-2$
		signalType.setAttribute("LONG-NAME", "Signal"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(signalType);
		Element signalAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		signalType.appendChild(signalAttributes);
		signalAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-SIGNAL-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element portType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		portType.setAttribute("IDENTIFIER", "TYPE-PORT"); //$NON-NLS-1$ //$NON-NLS-2$
		portType.setAttribute("LONG-NAME", "Port"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(portType);
		Element portAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		portType.appendChild(portAttributes);
		portAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-PORT-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		portAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-PORT-DATATYPE", //$NON-NLS-1$ //$NON-NLS-2$
				"Data Type", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		portAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-PORT-DESCRIPTION", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element signalAspectType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		signalAspectType.setAttribute("IDENTIFIER", "TYPE-SIGNALASPECT"); //$NON-NLS-1$ //$NON-NLS-2$
		signalAspectType.setAttribute("LONG-NAME", "SignalAspect"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(signalAspectType);
		Element signalAspectAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		signalAspectType.appendChild(signalAspectAttributes);
		signalAspectAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-SA-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		signalAspectAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-SA-TEXT", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		for (int i = 1; i < SIGNAL_ASPECT_PROPERTIES.length; i++) {
			signalAspectAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-SA-" + i, //$NON-NLS-1$ //$NON-NLS-2$
					SIGNAL_ASPECT_PROPERTIES[i], "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-LONGVALUE")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		Element specificationType = doc.createElementNS(REQIF_NS, "SPECIFICATION-TYPE"); //$NON-NLS-1$
		specificationType.setAttribute("IDENTIFIER", "TYPE-MODULE"); //$NON-NLS-1$ //$NON-NLS-2$
		specificationType.setAttribute("LONG-NAME", "Module"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(specificationType);

		Element specObjects = doc.createElementNS(REQIF_NS, "SPEC-OBJECTS"); //$NON-NLS-1$
		content.appendChild(specObjects);
		Element specifications = doc.createElementNS(REQIF_NS, "SPECIFICATIONS"); //$NON-NLS-1$
		content.appendChild(specifications);

		for (Package root : roots) {
			Element specification = doc.createElementNS(REQIF_NS, "SPECIFICATION"); //$NON-NLS-1$
			specification.setAttribute("IDENTIFIER", "SPEC-" + fragmentId(root)); //$NON-NLS-1$ //$NON-NLS-2$
			specification.setAttribute("LONG-NAME", root.getName()); //$NON-NLS-1$
			Element specType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			specification.appendChild(specType);
			appendText(doc, specType, "SPECIFICATION-TYPE-REF", "TYPE-MODULE"); //$NON-NLS-1$ //$NON-NLS-2$
			Element rootChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
			specification.appendChild(rootChildren);
			specifications.appendChild(specification);

			ResourceSet resourceSet = root.eResource().getResourceSet();
			appendPackageContents(doc, root, rootChildren, specObjects, resourceSet, counts, imageAttachments);
		}
		return doc;
	}

	/**
	 * Recursively appends every one of {@code pkg}'s own associated diagrams and
	 * Tables (all of them, not just one of each - see {@link
	 * #findAllDiagrams(ResourceSet, EObject)}/ {@link #findAllTables(ResourceSet,
	 * EObject)} - always first, matching the order they appear in the Model
	 * Explorer), its own packaged Packages (as nested {@code SPEC-HIERARCHY}
	 * headings, unconditionally - even ones with no Requirement of their own,
	 * matching the team's DOORS export convention), «Requirement» elements, {@link
	 * UseCase} elements, and {@code Block}- or {@code InterfaceBlock}-stereotyped
	 * {@link Class}es to {@code parentChildren}, preserving the original {@code
	 * packagedElements} order. Anything else is silently skipped.
	 */
	private static void appendPackageContents(Document doc, Package pkg, Element parentChildren, Element specObjects,
			ResourceSet resourceSet, int[] counts, Map<String, byte[]> imageAttachments) {
		for (Diagram diagram : findAllDiagrams(resourceSet, pkg)) {
			appendDiagramHierarchy(doc, diagram, parentChildren, specObjects, imageAttachments);
		}
		for (Table table : findAllTables(resourceSet, pkg)) {
			appendTableHierarchy(doc, table, parentChildren, specObjects, resourceSet, imageAttachments);
		}

		for (PackageableElement element : pkg.getPackagedElements()) {
			if (element instanceof Package) {
				Package child = (Package) element;
				String id = fragmentId(child);

				Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
				specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				specObject.setAttribute("LONG-NAME", child.getName()); //$NON-NLS-1$
				Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
				specObject.appendChild(values);
				values.appendChild(
						xhtmlValue(doc, child.getName(), "ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-HEADING-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
				Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
				specObject.appendChild(objType);
				appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-HEADING"); //$NON-NLS-1$ //$NON-NLS-2$
				specObjects.appendChild(specObject);

				Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
				hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
				hierarchy.appendChild(object);
				appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				Element childChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
				hierarchy.appendChild(childChildren);
				parentChildren.appendChild(hierarchy);

				counts[1]++;
				appendPackageContents(doc, child, childChildren, specObjects, resourceSet, counts, imageAttachments);
				continue;
			}

			if (element instanceof UseCase) {
				UseCase useCase = (UseCase) element;
				String id = fragmentId(useCase);

				Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
				specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				specObject.setAttribute("LONG-NAME", useCase.getName()); //$NON-NLS-1$
				Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
				specObject.appendChild(values);
				values.appendChild(
						stringValue(doc, useCase.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-USECASE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
				// Additional information: the first Comment owned by the UseCase
				String description = firstUndisplayedCommentText(useCase, resourceSet);
				if (description != null) {
					values.appendChild(boldPreservingXhtmlValue(doc, description, resourceSet,
							"ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-USECASE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$
				}
				Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
				specObject.appendChild(objType);
				appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-USECASE"); //$NON-NLS-1$ //$NON-NLS-2$
				specObjects.appendChild(specObject);

				Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
				hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
				hierarchy.appendChild(object);
				appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				parentChildren.appendChild(hierarchy);

				// Every Sequence Diagram belonging to this UseCase, if any - confirmed by
				// reading the real model that only the "Main Success Scenario" Interaction is
				// ever set as classifierBehavior, while every "Alternative Scenario" Interaction
				// is *also* an ownedBehavior of the same UseCase without being its
				// classifierBehavior (e.g. LC_UC2.3 has one classifierBehavior Interaction plus
				// several sibling ownedBehavior Interactions for its alternative scenarios) -
				// checking classifierBehavior alone silently missed the large majority of SDs
				// (35 total Sequence Diagrams in the model, only 7 UseCases have a
				// classifierBehavior at all). Iterating ownedBehaviors covers all of them,
				// classifierBehavior included, since it's always one of the ownedBehaviors too.
				Element useCaseChildren = null;
				for (Behavior behavior : useCase.getOwnedBehaviors()) {
					Diagram sd = findDiagram(resourceSet, behavior);
					if (sd == null) {
						continue;
					}
					if (useCaseChildren == null) {
						useCaseChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
						hierarchy.appendChild(useCaseChildren);
					}
					appendDiagramHierarchy(doc, sd, useCaseChildren, specObjects, imageAttachments);
				}

				counts[2]++;
				continue;
			}

			if (element instanceof Signal) {
				// The Signal itself (e.g. a Command "Activation" «InformationObject») is
				// deliberately never turned into its own work item - only whatever Diagram(s)/
				// Table(s) are nested under it are surfaced, flattened directly into this
				// Signal\'s own parent. The Signal only gives the Table a context.
				for (Diagram diagram : findAllDiagrams(resourceSet, element)) {
					appendDiagramHierarchy(doc, diagram, parentChildren, specObjects, imageAttachments);
				}
				for (Table table : findAllTables(resourceSet, element)) {
					appendTableHierarchy(doc, table, parentChildren, specObjects, resourceSet, imageAttachments);
				}
				continue;
			}

			if (!(element instanceof Class)) {
				continue;
			}

			if (hasStereotype((Class) element, INTERFACE_BLOCK_STEREOTYPE) || hasStereotype((Class) element, BLOCK_STEREOTYPE)) {
				appendBlockHierarchy(doc, (Class) element, parentChildren, specObjects, resourceSet, counts,
						imageAttachments);
				continue;
			}

			if (hasStereotype((Class) element, SIGNAL_ASPECT_STEREOTYPE)) {
				appendSignalAspect(doc, (Class) element, parentChildren, specObjects, resourceSet);
				counts[5]++;
				continue;
			}

			Class requirement = (Class) element;
			Stereotype stereotype = requirement.getAppliedStereotype(REQUIREMENT_STEREOTYPE);
			if (stereotype == null) {
				continue;
			}
			String text = String.valueOf(requirement.getValue(stereotype, "text")); //$NON-NLS-1$
			String id = fragmentId(requirement);

			Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
			specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
			specObject.setAttribute("LONG-NAME", requirement.getName()); //$NON-NLS-1$
			Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
			specObject.appendChild(values);
			values.appendChild(stringValue(doc, requirement.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
			values.appendChild(boldPreservingXhtmlValue(doc, text, resourceSet, "ATTRIBUTE-DEFINITION-XHTML-REF", //$NON-NLS-1$
					"ATTR-TEXT")); //$NON-NLS-1$
			Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			specObject.appendChild(objType);
			appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-REQUIREMENT"); //$NON-NLS-1$ //$NON-NLS-2$
			specObjects.appendChild(specObject);

			Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
			hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
			Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
			hierarchy.appendChild(object);
			appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
			parentChildren.appendChild(hierarchy);

			counts[0]++;
		}
	}

	/**
	 * Appends one "SignalAspect"-typed leaf work item: the element's name as
	 * {@code ReqIF.Name}, "Aspect meaning" as the rich-text Description
	 * ({@code ReqIF.Text}, omitted if empty), and every other stereotype
	 * property as its own string attribute (omitted if unset).
	 */
	private static void appendSignalAspect(Document doc, Class element, Element parentChildren, Element specObjects,
			ResourceSet resourceSet) {
		Stereotype stereotype = null;
		for (Stereotype candidate : element.getAppliedStereotypes()) {
			if (SIGNAL_ASPECT_STEREOTYPE.equals(candidate.getName())) {
				stereotype = candidate;
				break;
			}
		}
		String id = fragmentId(element);

		Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
		specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		specObject.setAttribute("LONG-NAME", element.getName()); //$NON-NLS-1$
		Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
		specObject.appendChild(values);
		values.appendChild(stringValue(doc, element.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-SA-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < SIGNAL_ASPECT_PROPERTIES.length; i++) {
			Object raw = stereotype == null ? null : element.getValue(stereotype, SIGNAL_ASPECT_PROPERTIES[i]);
			String value = raw == null ? "" : String.valueOf(raw); //$NON-NLS-1$
			if (value.isEmpty()) {
				continue;
			}
			if (i == 0) {
				values.appendChild(xhtmlValue(doc, value, "ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-SA-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				values.appendChild(stringValue(doc, value, "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-SA-" + i)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		specObject.appendChild(objType);
		appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-SIGNALASPECT"); //$NON-NLS-1$ //$NON-NLS-2$
		specObjects.appendChild(specObject);

		Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
		hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
		hierarchy.appendChild(object);
		appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		parentChildren.appendChild(hierarchy);
	}

	private static boolean hasStereotype(Class element, String simpleName) {
		for (Stereotype stereotype : element.getAppliedStereotypes()) {
			if (simpleName.equals(stereotype.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Appends one "Block"-typed leaf {@code SPEC-HIERARCHY} node for a
	 * {@code Block} or {@code InterfaceBlock} (e.g. {@code SCI_LC_Command}, or
	 * a plain SysML Block like "Subsystem - Level Crossing"), with:
	 * <ul>
	 * <li>every Diagram/Table found directly on the Block itself (its own
	 * IBD/BDD) nested as "Diagram" children;</li>
	 * <li>the diagrams of its classifier behavior (State Machine Diagram);</li>
	 * <li>its FlowPorts and every {@code DirectedFeature}-stereotyped
	 * {@link Feature} (its signals - {@code Class.getFeatures()} covers
	 * attributes, operations and receptions alike) nested underneath.</li>
	 * </ul>
	 */
	private static void appendBlockHierarchy(Document doc, Class block, Element parentChildren, Element specObjects,
			ResourceSet resourceSet, int[] counts, Map<String, byte[]> imageAttachments) {
		String id = fragmentId(block);

		Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
		specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		specObject.setAttribute("LONG-NAME", block.getName()); //$NON-NLS-1$
		Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
		specObject.appendChild(values);
		values.appendChild(stringValue(doc, block.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-BLOCK-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
		Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		specObject.appendChild(objType);
		appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-BLOCK"); //$NON-NLS-1$ //$NON-NLS-2$
		specObjects.appendChild(specObject);

		Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
		hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
		hierarchy.appendChild(object);
		appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element children = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
		hierarchy.appendChild(children);
		parentChildren.appendChild(hierarchy);

		counts[3]++;

		for (Diagram diagram : findAllDiagrams(resourceSet, block)) {
			appendDiagramHierarchy(doc, diagram, children, specObjects, imageAttachments);
		}
		for (Table table : findAllTables(resourceSet, block)) {
			appendTableHierarchy(doc, table, children, specObjects, resourceSet, imageAttachments);
		}

		// A State Machine Diagram's own semantic element is the classifierBehavior StateMachine itself,
		// never the Block, so it is looked up separately (findAllDiagrams above only finds
		// diagrams bound directly to the Block).
		Behavior classifierBehavior = block.getClassifierBehavior();
		if (classifierBehavior != null) {
			for (Diagram diagram : findAllDiagrams(resourceSet, classifierBehavior)) {
				appendDiagramHierarchy(doc, diagram, children, specObjects, imageAttachments);
			}
		}

		// Every FlowPort owned directly by this Block - exported once from the Block's own
		// definition, never once per occurrence of the Block inside a larger diagram.
		for (Property attribute : block.getOwnedAttributes()) {
			if (attribute instanceof Port) {
				appendPortHierarchy(doc, (Port) attribute, children, specObjects, resourceSet);
			}
		}

		for (Feature feature : block.getFeatures()) {
			if (!hasFeatureStereotype(feature, DIRECTED_FEATURE_STEREOTYPE)) {
				continue;
			}
			String signalId = fragmentId(feature);

			Element signalObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
			signalObject.setAttribute("IDENTIFIER", "SO-" + signalId); //$NON-NLS-1$ //$NON-NLS-2$
			signalObject.setAttribute("LONG-NAME", feature.getName()); //$NON-NLS-1$
			Element signalValues = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
			signalObject.appendChild(signalValues);
			signalValues.appendChild(
					stringValue(doc, feature.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-SIGNAL-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
			Element signalObjType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			signalObject.appendChild(signalObjType);
			appendText(doc, signalObjType, "SPEC-OBJECT-TYPE-REF", "TYPE-SIGNAL"); //$NON-NLS-1$ //$NON-NLS-2$
			specObjects.appendChild(signalObject);

			Element signalHierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
			signalHierarchy.setAttribute("IDENTIFIER", "SH-" + signalId); //$NON-NLS-1$ //$NON-NLS-2$
			Element signalHierarchyObject = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
			signalHierarchy.appendChild(signalHierarchyObject);
			appendText(doc, signalHierarchyObject, "SPEC-OBJECT-REF", "SO-" + signalId); //$NON-NLS-1$ //$NON-NLS-2$
			children.appendChild(signalHierarchy);

			counts[4]++;
		}
	}

	/** Appends one "Port"-typed leaf {@code SPEC-HIERARCHY} node (Name, Data Type, and - from the port's first owned {@link Comment}, if any - a rich-text Description) to {@code parentChildren} - mirrors {@code ExportTransitionInformationHandler.appendPortHierarchy}, independently duplicated per this plugin's convention. */
	private static void appendPortHierarchy(Document doc, Port port, Element parentChildren, Element specObjects,
			ResourceSet resourceSet) {
		String id = fragmentId(port);
		Type dataType = port.getType();

		Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
		specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		specObject.setAttribute("LONG-NAME", port.getName()); //$NON-NLS-1$
		Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
		specObject.appendChild(values);
		values.appendChild(stringValue(doc, port.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-PORT-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
		values.appendChild(stringValue(doc, dataType == null ? "" : dataType.getName(), //$NON-NLS-1$
				"ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-PORT-DATATYPE")); //$NON-NLS-1$ //$NON-NLS-2$
		Comment comment = port.getOwnedComments().isEmpty() ? null : port.getOwnedComments().get(0);
		// Polarion imports these as plain "Text" items without a title field, so the port name
		// is also written as the bold first line of the description, Comment text underneath.
		String portName = port.getName() == null ? "" : port.getName() //$NON-NLS-1$
				.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		String commentBody = comment == null || comment.getBody() == null ? "" : comment.getBody(); //$NON-NLS-1$
		values.appendChild(boldPreservingXhtmlValue(doc, "<b>" + portName + "</b>" + (commentBody.isEmpty() ? "" : "\n" + commentBody), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				resourceSet, "ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-PORT-DESCRIPTION")); //$NON-NLS-1$ //$NON-NLS-2$
		Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		specObject.appendChild(objType);
		appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-PORT"); //$NON-NLS-1$ //$NON-NLS-2$
		specObjects.appendChild(specObject);

		Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
		hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
		hierarchy.appendChild(object);
		appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		parentChildren.appendChild(hierarchy);
	}

	private static boolean hasFeatureStereotype(Feature feature, String simpleName) {
		for (Stereotype stereotype : feature.getAppliedStereotypes()) {
			if (simpleName.equals(stereotype.getName())) {
				return true;
			}
		}
		return false;
	}

	/** The first {@link Diagram} view in {@code resourceSet} whose semantic element is (by identity) {@code element} - takes any notation type, unlike {@code ExportTransitionInformationHandler.findDiagram}. Used only for the per-{@link Behavior} Sequence Diagram lookup, where a single Interaction having more than one diagram has never been observed in this model - {@link #findAllDiagrams(ResourceSet, EObject)} is used everywhere multiple diagrams per element are a real possibility (e.g. a Package). */
	private static Diagram findDiagram(ResourceSet resourceSet, EObject element) {
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".notation")) { //$NON-NLS-1$
				continue;
			}
			for (EObject content : resource.getContents()) {
				if (content instanceof Diagram && ((Diagram) content).getElement() == element) {
					return (Diagram) content;
				}
			}
		}
		return null;
	}

	/**
	 * Every {@link Diagram} view in {@code resourceSet} whose semantic
	 * element is (by identity) {@code element}, in the order they're found
	 * across `.notation` resources - takes any notation type, since a
	 * Package's own diagrams can be a BDD, UCD, etc. depending on context.
	 */
	private static List<Diagram> findAllDiagrams(ResourceSet resourceSet, EObject element) {
		List<Diagram> result = new ArrayList<>();
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".notation")) { //$NON-NLS-1$
				continue;
			}
			for (EObject content : resource.getContents()) {
				if (content instanceof Diagram && ((Diagram) content).getElement() == element) {
					result.add((Diagram) content);
				}
			}
		}
		return result;
	}

	/**
	 * Every Papyrus NatTable {@link Table} in {@code resourceSet} whose {@code
	 * context} is (by identity) {@code element} - the same {@code .notation}
	 * resource GMF {@link Diagram}s live in also holds every {@code nattable:Table}
	 * as a top-level sibling (confirmed by reading the real model's XMI), each with
	 * a required {@code context} reference documented in Papyrus's own NatTable
	 * Ecore model as "the 'virtual' parent to display the table in the Papyrus Model
	 * Explorer" - exactly the Package (or other element) a Table appears nested
	 * under in the tree, the same relationship {@link Diagram#getElement()} plays
	 * for diagrams.
	 */
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

	/** Appends one "Diagram"-typed leaf {@code SPEC-HIERARCHY} node (name + embedded image, see {@link #diagramImageValue(Document, Diagram, String, String, Map)}) to {@code parentChildren}. */
	private static void appendDiagramHierarchy(Document doc, Diagram diagram, Element parentChildren, Element specObjects,
			Map<String, byte[]> imageAttachments) {
		String id = fragmentId(diagram);

		Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
		specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		specObject.setAttribute("LONG-NAME", diagram.getName()); //$NON-NLS-1$
		Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
		specObject.appendChild(values);
		values.appendChild(stringValue(doc, diagram.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-DIAGRAM-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
		values.appendChild(diagramImageValue(doc, diagram, "ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-DIAGRAM-IMAGE", //$NON-NLS-1$ //$NON-NLS-2$
				imageAttachments));
		Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		specObject.appendChild(objType);
		appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-DIAGRAM"); //$NON-NLS-1$ //$NON-NLS-2$
		specObjects.appendChild(specObject);

		Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
		hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
		hierarchy.appendChild(object);
		appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		parentChildren.appendChild(hierarchy);
	}

	/**
	 * Renders {@code diagram} to a throwaway temporary PNG (via the same,
	 * official GMF {@link CopyToImageUtil} {@code ExportAllDiagramsHandler}
	 * already uses for "Export All Diagrams..."), then references it via a
	 * real {@code <xhtml:object type="image/png" data="images/<id>.png">}
	 * pointing at a file inside the exported {@code .reqifz} package - the
	 * bytes are added to {@code imageAttachments} here and actually written
	 * into the zip later by {@link #writeReqifZ}. Deliberately <b>not</b> a
	 * base64 {@code data:} URI (what shipped first): Polarion's work-item
	 * view rendered that field completely empty on import - confirmed by
	 * comparing against a real reference DOORS/Windchill `.reqifz` export,
	 * which represents embedded images exactly this way (an external file
	 * inside the package, referenced via a relative {@code data} path)
	 * rather than inline base64. Falls back to a plain-text placeholder if
	 * rendering fails for any reason.
	 */
	private static Element diagramImageValue(Document doc, Diagram diagram, String defRefTag, String defRefValue,
			Map<String, byte[]> imageAttachments) {
		try {
			File temp = File.createTempFile("reqif-diagram-", ".svg"); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				if (diagram.eResource() != null) {
					ensurePageOpen(diagram, diagram.eResource().getResourceSet());
				}
				// copyToImage(Diagram, ...) builds its own throwaway off-screen edit part, which lays out
				// FlowPort arrows wrongly. Papyrus's own "Save as Image" uses the open editor's live
				// DiagramEditPart - so use that whenever one can be found.
				// so use that whenever one can be found, and only fall back to the off-screen path otherwise.
				DiagramEditPart live = diagram.eResource() == null ? null
						: findLiveDiagramEditPart(diagram, diagram.eResource().getResourceSet());
				if (live != null) {
					new FastSvgCopyToImageUtil().copyToImage(live, new Path(temp.getAbsolutePath()), ImageFileFormat.SVG,
							new NullProgressMonitor());
				} else {
					new FastSvgCopyToImageUtil().copyToImage(diagram, new Path(temp.getAbsolutePath()), ImageFileFormat.SVG,
							new NullProgressMonitor(), PreferencesHint.USE_DEFAULTS);
				}
				// SVG is vector, so no cropToContent here (that is a pixel-based step for raster images).
				byte[] bytes = Files.readAllBytes(temp.toPath());
				String relativePath = "images/" + fragmentId(diagram) + ".svg"; //$NON-NLS-1$ //$NON-NLS-2$
				imageAttachments.put(relativePath, bytes);

				Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-XHTML"); //$NON-NLS-1$
				Element theValue = doc.createElementNS(REQIF_NS, "THE-VALUE"); //$NON-NLS-1$
				attributeValue.appendChild(theValue);
				Element div = doc.createElementNS(XHTML_NS, "xhtml:div"); //$NON-NLS-1$
				theValue.appendChild(div);
				// A Sequence/State Machine Diagram's Interaction/StateMachine may carry a Comment with additional information:
				// shown above the image, in the same work item.
				if ((diagram.getElement() instanceof org.eclipse.uml2.uml.Interaction
						|| diagram.getElement() instanceof org.eclipse.uml2.uml.StateMachine) && diagram.eResource() != null) {
					String intro = firstUndisplayedCommentText(diagram.getElement(), diagram.eResource().getResourceSet());
					if (intro != null) {
						appendPlainTextLines(doc, div,
								plainTextFromHtml(intro, diagram.eResource().getResourceSet()));
						div.appendChild(doc.createElementNS(XHTML_NS, "xhtml:br")); //$NON-NLS-1$
					}
				}
				Element object = doc.createElementNS(XHTML_NS, "xhtml:object"); //$NON-NLS-1$
				object.setAttribute("type", "image/svg+xml"); //$NON-NLS-1$ //$NON-NLS-2$
				object.setAttribute("data", relativePath); //$NON-NLS-1$
				object.setTextContent(diagram.getName());
				div.appendChild(object);
				Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
				attributeValue.appendChild(definition);
				appendText(doc, definition, defRefTag, defRefValue);
				return attributeValue;
			} finally {
				temp.delete();
			}
		} catch (CoreException | RuntimeException | IOException e) {
			return xhtmlValue(doc, "[Diagram image could not be rendered: " + e.getMessage() + "]", defRefTag, //$NON-NLS-1$ //$NON-NLS-2$
					defRefValue);
		}
	}

	/**
	 * Returns {@code null} (rather than throwing) if no open editor for this model
	 * can be found, or if anything here fails - every caller treats that as
	 * "couldn't ensure a live editor context", a pure best-effort improvement that
	 * never makes the existing off-screen fallback path any worse than it already
	 * was.
	 */
	private static IPageManager findPageManager(ResourceSet resourceSet) {
		try {
			for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					for (IEditorReference ref : page.getEditorReferences()) {
						IEditorPart part = ref.getEditor(false);
						if (!(part instanceof IMultiDiagramEditor)) {
							continue;
						}
						ServicesRegistry registry = ((IMultiDiagramEditor) part).getServicesRegistry();
						try {
							if (registry.getService(ModelSet.class) == resourceSet) {
								return registry.getService(IPageManager.class);
							}
						} catch (Exception e) {
							// Not the right editor for this model (or getService threw one of
							// Papyrus's several checked service-lookup exceptions) - keep looking.
						}
					}
				}
			}
		} catch (RuntimeException e) {
			// Best effort only - fall through to null below.
		}
		return null;
	}

	/**
	 * The live {@link DiagramEditPart} of {@code diagram}, if it is the active
	 * page of an open Papyrus editor backed by {@code resourceSet} (see
	 * {@link #ensurePageOpen}) - {@code null} otherwise, in which case the
	 * caller falls back to off-screen rendering.
	 */
	private static DiagramEditPart findLiveDiagramEditPart(Diagram diagram, ResourceSet resourceSet) {
		try {
			for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					for (IEditorReference ref : page.getEditorReferences()) {
						IEditorPart part = ref.getEditor(false);
						if (!(part instanceof IMultiDiagramEditor)) {
							continue;
						}
						IMultiDiagramEditor multi = (IMultiDiagramEditor) part;
						try {
							if (multi.getServicesRegistry().getService(ModelSet.class) != resourceSet) {
								continue;
							}
						} catch (Exception e) {
							continue;
						}
						IEditorPart active = multi.getActiveEditor();
						IDiagramWorkbenchPart diagramPart = active instanceof IDiagramWorkbenchPart
								? (IDiagramWorkbenchPart) active
								: active == null ? null : active.getAdapter(IDiagramWorkbenchPart.class);
						if (diagramPart != null && diagramPart.getDiagram() == diagram) {
							return diagramPart.getDiagramEditPart();
						}
					}
				}
			}
		} catch (RuntimeException e) {
			// Best effort only - fall through to null below.
		}
		return null;
	}

	/**
	 * Opens {@code pageElement} (a Diagram or Table) as a real page in
	 * whichever already-open Papyrus editor backs {@code resourceSet}, if
	 * any - see {@link #findPageManager(ResourceSet)} for why. Does nothing
	 * (silently) if no such editor is open, {@code pageElement} is already
	 * open, or anything here fails - the existing off-screen rendering path
	 * still runs afterward exactly as before in every one of those cases.
	 */
	/** Pages (pageManager, diagram) this export run opened itself - closed again by {@link #closePagesOpenedByExport()}; pages that were already open before are never recorded here, so they stay open. */
	private static final List<Object[]> OPENED_BY_EXPORT = new ArrayList<>();

	private static void closePagesOpenedByExport() {
		for (Object[] opened : OPENED_BY_EXPORT) {
			try {
				((IPageManager) opened[0]).closePage(opened[1]);
			} catch (RuntimeException e) {
				// Best effort only - a page that can't be closed just stays open.
			}
		}
		OPENED_BY_EXPORT.clear();
	}

	private static void ensurePageOpen(EObject pageElement, ResourceSet resourceSet) {
		try {
			IPageManager pageManager = findPageManager(resourceSet);
			if (pageManager != null) {
				if (pageManager.isOpen(pageElement)) {
					// already open, but must be the *active* page for findLiveDiagramEditPart to see it
					pageManager.selectPage(pageElement);
				} else {
					pageManager.openPage(pageElement, null);
					OPENED_BY_EXPORT.add(new Object[] { pageManager, pageElement });
				}
				// openPage() only schedules the page's creation - pump the display so the edit parts
				// are laid out before CopyToImageUtil grabs them.
				Display display = Display.getCurrent();
				if (display != null) {
					while (display.readAndDispatch()) {
						// drain the queue
					}
				}
			}
		} catch (RuntimeException e) {
			// Best effort only.
		}
	}

	/**
	 * This is a plain post-processing step on the finished PNG bytes, not a change
	 * to how any diagram/table is rendered - it works identically for {@link
	 * #diagramImageValue} and {@link #tableImageValue}. Falls back to returning the
	 * original bytes unchanged if decoding fails, or if there's nothing worth
	 * cropping (already tight, or fully blank).
	 */
	private static byte[] cropToContent(byte[] pngBytes) {
		try {
			ImageData data = new ImageLoader().load(new ByteArrayInputStream(pngBytes))[0];
			int minX = data.width;
			int minY = data.height;
			int maxX = -1;
			int maxY = -1;
			for (int y = 0; y < data.height; y++) {
				for (int x = 0; x < data.width; x++) {
					if (isContentPixel(data, x, y)) {
						minX = Math.min(minX, x);
						minY = Math.min(minY, y);
						maxX = Math.max(maxX, x);
						maxY = Math.max(maxY, y);
					}
				}
			}
			if (maxX < 0 || maxY < 0) {
				return pngBytes; // fully blank - nothing to crop, leave as-is
			}
			int margin = 4;
			int cropX = Math.max(0, minX - margin);
			int cropY = Math.max(0, minY - margin);
			int cropWidth = Math.min(data.width - 1, maxX + margin) - cropX + 1;
			int cropHeight = Math.min(data.height - 1, maxY + margin) - cropY + 1;
			if (cropX == 0 && cropY == 0 && cropWidth == data.width && cropHeight == data.height) {
				return pngBytes; // already tight - avoid a pointless re-encode
			}
			ImageData cropped = new ImageData(cropWidth, cropHeight, data.depth, data.palette);
			cropped.alphaData = data.alphaData != null ? new byte[cropWidth * cropHeight] : null;
			for (int y = 0; y < cropHeight; y++) {
				for (int x = 0; x < cropWidth; x++) {
					cropped.setPixel(x, y, data.getPixel(cropX + x, cropY + y));
					if (data.alphaData != null) {
						cropped.setAlpha(x, y, data.getAlpha(cropX + x, cropY + y));
					}
				}
			}
			ImageLoader saver = new ImageLoader();
			saver.data = new ImageData[] { cropped };
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			saver.save(out, SWT.IMAGE_PNG);
			return out.toByteArray();
		} catch (Exception e) {
			return pngBytes;
		}
	}

	private static boolean isContentPixel(ImageData data, int x, int y) {
		if (data.alphaData != null && data.getAlpha(x, y) == 0) {
			return false;
		}
		RGB rgb = data.palette.getRGB(data.getPixel(x, y));
		return !(rgb.red > 250 && rgb.green > 250 && rgb.blue > 250);
	}

	/**
	 * Es soll Teil des work items der Tabelle sein"): matching how DOORS models an
	 * "Info" object as one free-text paragraph immediately followed by its table,
	 * this looks up the first {@code Comment} owned by the Table's own {@code
	 * context} element (the same element a Table appears nested under in the Model
	 * Explorer - see {@link #findAllTables}) and, if present, has {@link
	 * #tableImageValue} prepend its text to the *same* Description value the image
	 * goes into, rather than creating any separate Requirement leaf for it. A
	 * Package with more than one Table sharing the same context would currently
	 * share this same intro Comment too - not a problem for the concrete case this
	 * was built for (one Table, one intro paragraph, per context element), revisit
	 * if it ever actually comes up.
	 */
	private static void appendTableHierarchy(Document doc, Table table, Element parentChildren, Element specObjects,
			ResourceSet resourceSet, Map<String, byte[]> imageAttachments) {
		String id = fragmentId(table);
		String name = table.getName();
		String introText = firstOwnedCommentText(table.getContext());

		Element specObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
		specObject.setAttribute("IDENTIFIER", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		specObject.setAttribute("LONG-NAME", name); //$NON-NLS-1$
		Element values = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
		specObject.appendChild(values);
		values.appendChild(stringValue(doc, name, "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-DIAGRAM-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
		values.appendChild(tableImageValue(doc, table, introText, resourceSet, "ATTRIBUTE-DEFINITION-XHTML-REF", //$NON-NLS-1$
				"ATTR-DIAGRAM-IMAGE", imageAttachments)); //$NON-NLS-1$
		Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		specObject.appendChild(objType);
		appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-DIAGRAM"); //$NON-NLS-1$ //$NON-NLS-2$
		specObjects.appendChild(specObject);

		Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
		hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
		hierarchy.appendChild(object);
		appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
		parentChildren.appendChild(hierarchy);
	}

	/**
	 * Embeds {@code table}'s image into this "Diagram"-typed leaf - the
	 * NatTable equivalent of
	 * {@link #diagramImageValue(Document, Diagram, String, String, Map)} -
	 * same {@code <xhtml:object type="image/png" data="images/<id>.png">}
	 * embedding into the {@code .reqifz} package, same plain-text-
	 * placeholder fallback when no image is available.
	 * <p>
	 * This method now just looks for the file that pre-step is expected to
	 * produce - {@code <repo root>/sync/tables/<sanitized table name>.png}
	 * (see {@link #resolveTableImageFile(Table)}) - and embeds it directly
	 * if found (still {@link #cropToContent(byte[])}ped, in case a future
	 * rendering mechanism ever reintroduces excess canvas the way GMF's
	 * {@code CopyToImageUtil} did for Sequence Diagrams). Falls back to a
	 * plain-text placeholder naming the expected path if the file isn't
	 * there yet, rather than aborting the whole export over one table.
	 * <p>
	 * {@code introText}, if non-null/non-empty (see
	 * {@link #firstOwnedCommentText}), is refreshed/flattened via
	 * {@link #plainTextFromHtml} and written as plain lines *before* the
	 * image, inside the very same {@code <xhtml:div>} - one combined
	 * Description value, not two separate work items, matching how DOORS
	 * models an "Info" object's intro paragraph immediately followed by
	 * its table as a single object.
	 */
	private static Element tableImageValue(Document doc, Table table, String introText, ResourceSet resourceSet,
			String defRefTag, String defRefValue, Map<String, byte[]> imageAttachments) {
		File preRendered = resolveTableImageFile(table);
		if (preRendered == null || !preRendered.isFile()) {
			String message = "[Table image not found - run \"Export Tables...\" first, expected at " //$NON-NLS-1$
					+ (preRendered == null ? "<unresolved path>" : preRendered.getAbsolutePath()) + "]"; //$NON-NLS-1$ //$NON-NLS-2$
			String combined = introText != null && !introText.isEmpty()
					? plainTextFromHtml(introText, resourceSet) + "\n" + message //$NON-NLS-1$
					: message;
			return xhtmlValue(doc, combined, defRefTag, defRefValue);
		}
		try {
			byte[] bytes = Files.readAllBytes(preRendered.toPath());
			String relativePath = "images/" + fragmentId(table) + ".svg"; //$NON-NLS-1$ //$NON-NLS-2$
			imageAttachments.put(relativePath, bytes);

			Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-XHTML"); //$NON-NLS-1$
			Element theValue = doc.createElementNS(REQIF_NS, "THE-VALUE"); //$NON-NLS-1$
			attributeValue.appendChild(theValue);
			Element div = doc.createElementNS(XHTML_NS, "xhtml:div"); //$NON-NLS-1$
			theValue.appendChild(div);
			if (introText != null && !introText.isEmpty()) {
				appendPlainTextLines(doc, div, plainTextFromHtml(introText, resourceSet));
				div.appendChild(doc.createElementNS(XHTML_NS, "xhtml:br")); //$NON-NLS-1$
			}
			Element object = doc.createElementNS(XHTML_NS, "xhtml:object"); //$NON-NLS-1$
			object.setAttribute("type", "image/svg+xml"); //$NON-NLS-1$ //$NON-NLS-2$
			object.setAttribute("data", relativePath); //$NON-NLS-1$
			object.setTextContent(table.getName());
			div.appendChild(object);
			Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
			attributeValue.appendChild(definition);
			appendText(doc, definition, defRefTag, defRefValue);
			return attributeValue;
		} catch (IOException | RuntimeException e) {
			String message = "[Table image could not be read: " + e.getMessage() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
			String combined = introText != null && !introText.isEmpty()
					? plainTextFromHtml(introText, resourceSet) + "\n" + message //$NON-NLS-1$
					: message;
			return xhtmlValue(doc, combined, defRefTag, defRefValue);
		}
	}

	/**
	 * The file {@link ExportTablesHandler} is expected to have saved
	 * {@code table}'s pre-rendered image to - {@code <repo root>/sync/
	 * tables/<sanitized table name>.png}, mirroring
	 * {@link #guessDefaultOutputFolder}'s "sync/requirements" convention.
	 * Returns {@code null} if the owning project/repo root can't be
	 * resolved.
	 */
	private static File resolveTableImageFile(Table table) {
		if (table.eResource() == null) {
			return null;
		}
		IProject project = resolveProject(table.eResource());
		if (project == null || project.getLocation() == null) {
			return null;
		}
		File repoRoot = project.getLocation().toFile().getParentFile();
		if (repoRoot == null) {
			return null;
		}
		return new File(new File(new File(repoRoot, "sync"), "tables"), sanitize(table.getName()) + ".svg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static Element attributeDefinition(Document doc, String tag, String identifier, String longName,
			String typeRefTag, String typeRefValue) {
		Element definition = doc.createElementNS(REQIF_NS, tag);
		definition.setAttribute("IDENTIFIER", identifier); //$NON-NLS-1$
		definition.setAttribute("LONG-NAME", longName); //$NON-NLS-1$
		Element type = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
		definition.appendChild(type);
		appendText(doc, type, typeRefTag, typeRefValue);
		return definition;
	}

	private static Element stringValue(Document doc, String value, String defRefTag, String defRefValue) {
		Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-STRING"); //$NON-NLS-1$
		attributeValue.setAttribute("THE-VALUE", value == null ? "" : value); //$NON-NLS-1$ //$NON-NLS-2$
		Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
		attributeValue.appendChild(definition);
		appendText(doc, definition, defRefTag, defRefValue);
		return attributeValue;
	}

	/** XHTML content must be well-formed child markup (not a plain attribute), per the ReqIF schema - wrapped in a single {@code xhtml:div}, one {@code xhtml:br} per embedded newline. */
	private static Element xhtmlValue(Document doc, String value, String defRefTag, String defRefValue) {
		Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-XHTML"); //$NON-NLS-1$
		Element theValue = doc.createElementNS(REQIF_NS, "THE-VALUE"); //$NON-NLS-1$
		attributeValue.appendChild(theValue);
		Element div = doc.createElementNS(XHTML_NS, "xhtml:div"); //$NON-NLS-1$
		theValue.appendChild(div);
		appendPlainTextLines(doc, div, value);
		Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
		attributeValue.appendChild(definition);
		appendText(doc, definition, defRefTag, defRefValue);
		return attributeValue;
	}

	/** Appends {@code text} to {@code container} as plain XHTML content - one {@code xhtml:br} per embedded newline - shared by {@link #xhtmlValue} and {@link #tableImageValue}'s intro-paragraph-before-the-image case. */
	private static void appendPlainTextLines(Document doc, Element container, String text) {
		String[] lines = (text == null ? "" : text).split("\n", -1); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				container.appendChild(doc.createElementNS(XHTML_NS, "xhtml:br")); //$NON-NLS-1$
			}
			container.appendChild(doc.createTextNode(lines[i]));
		}
	}

	/**
	 * The body of the first {@link Comment} owned by {@code context}, or
	 * {@code null} if {@code context} isn't a UML {@code Element} at all or
	 * has no non-empty owned Comment - used to find a Table's "intro
	 * paragraph" (see {@link #appendTableHierarchy}), the same
	 * first-owned-Comment idiom
	 * {@code ExportTransitionInformationHandler.appendPortHierarchy} already
	 * uses for Port descriptions, independently duplicated here per this
	 * plugin's established convention.
	 */
	private static String firstOwnedCommentText(EObject context) {
		if (!(context instanceof org.eclipse.uml2.uml.Element)) {
			return null;
		}
		for (Comment comment : ((org.eclipse.uml2.uml.Element) context).getOwnedComments()) {
			if (comment.getBody() != null && !comment.getBody().isEmpty()) {
				return comment.getBody();
			}
		}
		return null;
	}

	/**
	 * Body of the first Comment owned by {@code context} that is not shown in any
	 * diagram of the model. Comments drawn as notes in a diagram (they have a
	 * notation view) are part of the diagram and not meant as description; a
	 * description Comment is created in the Model Explorer and has no view.
	 */
	private static String firstUndisplayedCommentText(EObject context, ResourceSet resourceSet) {
		if (!(context instanceof org.eclipse.uml2.uml.Element)) {
			return null;
		}
		java.util.Set<EObject> shown = null;
		for (Comment comment : ((org.eclipse.uml2.uml.Element) context).getOwnedComments()) {
			if (comment.getBody() == null || comment.getBody().isEmpty()) {
				continue;
			}
			if (shown == null) {
				shown = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<EObject, Boolean>());
				for (org.eclipse.emf.ecore.resource.Resource resource : new java.util.ArrayList<>(
						resourceSet.getResources())) {
					if (!"notation".equals(resource.getURI().fileExtension())) { //$NON-NLS-1$
						continue;
					}
					for (java.util.Iterator<EObject> it = resource.getAllContents(); it.hasNext();) {
						EObject content = it.next();
						if (content instanceof org.eclipse.gmf.runtime.notation.View) {
							EObject element = ((org.eclipse.gmf.runtime.notation.View) content).getElement();
							if (element != null) {
								shown.add(element);
							}
						}
					}
				}
			}
			if (!shown.contains(comment)) {
				return comment.getBody();
			}
		}
		return null;
	}

	/**
	 * Converts a stored rich-text value (this plugin's own restricted HTML
	 * dialect - see {@code richtext.HtmlStyledTextEditor}'s class comment for
	 * the exact tag subset) into plain, unformatted text for ReqIF export -
	 * used for «Requirement» {@code text}, which can carry the same
	 * bold/italic/reference-span markup as a Comment {@code body} (the same
	 * "Edit Requirement..." dialog and "Insert Reference..." feature write
	 * into it). {@link RichTextReferenceUtil#refresh} first brings any
	 * reference spans' displayed names up to date, same as opening and
	 * saving the Requirement in the model would; the markup is then
	 * stripped down to its visible text (a reference span becomes just the
	 * referenced element's current name, bold/italic/underline disappear,
	 * each bulleted line/paragraph becomes its own line) rather than kept
	 * as real ReqIF XHTML markup.
	 * <p>
	 * Checking the team's existing DOORS-based Polarion documents turned up the same
	 * limitation there too, so the team decided plain, unstyled text is an
	 * acceptable outcome here - the model itself stays the actual source of truth
	 * for a reference and is already kept current automatically (see "Automatic
	 * rich-text reference refresh on rename" further below), so losing the
	 * blue/underlined styling in just the ReqIF export wasn't worth chasing further.
	 */
	private static final Pattern LINE_BREAK_TAG_PATTERN = Pattern.compile("<br\\s*/?>|</p\\s*>", //$NON-NLS-1$
			Pattern.CASE_INSENSITIVE);
	private static final Pattern OPEN_P_TAG_PATTERN = Pattern.compile("<p\\b[^>]*>", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
	private static final Pattern ANY_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>"); //$NON-NLS-1$

	/** A literal tab in stored rich text (typed to visually space out terms, e.g. "value{@code \t}meaning") is otherwise lost on export: XHTML/HTML collapses any run of plain whitespace - tabs included - to a single space when rendered, the same category of gap as the {@code \n}-vs-{@code <br/>} line-break issue above. A non-breaking space (U+00A0) is NOT collapsed this way, so widening each tab to a short run of them preserves a visible gap. */
	private static final String TAB_REPLACEMENT = "    "; //$NON-NLS-1$

	private static String plainTextFromHtml(String html, ResourceSet resourceSet) {
		String refreshed = RichTextReferenceUtil.refresh(html, resourceSet);
		String text = LINE_BREAK_TAG_PATTERN.matcher(refreshed).replaceAll("\n"); //$NON-NLS-1$
		text = OPEN_P_TAG_PATTERN.matcher(text).replaceAll(""); //$NON-NLS-1$
		text = ANY_TAG_PATTERN.matcher(text).replaceAll(""); //$NON-NLS-1$
		text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				.replace("&apos;", "'").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		text = text.replace("\t", TAB_REPLACEMENT); //$NON-NLS-1$
		return text.strip();
	}

	/**
	 * Converts the stored rich-text HTML into ReqIF XHTML that keeps only bold
	 * ({@code <b>}, written with the {@code reqif-xhtml:} prefix Polarion
	 * recognizes); every other tag is stripped down to its text (a reference
	 * span becomes the referenced element's current name). Line breaks become
	 * {@code <reqif-xhtml:br/>} and tabs non-breaking spaces, since XHTML
	 * collapses plain whitespace. Falls back to {@link #plainTextFromHtml} if
	 * the markup cannot be parsed (e.g. plain text containing a bare
	 * {@code &}).
	 */
	private static final Pattern ANY_TAG_EXCEPT_B_PATTERN = Pattern.compile("</?(?!b\\b)[a-zA-Z][^>]*>", //$NON-NLS-1$
			Pattern.CASE_INSENSITIVE);
	private static final Pattern BOLD_TAG_PATTERN = Pattern.compile("<(/?)b\\b", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static Element boldPreservingXhtmlValue(Document doc, String html, ResourceSet resourceSet,
			String defRefTag, String defRefValue) {
		String refreshed = RichTextReferenceUtil.refresh(html, resourceSet);
		try {
			String text = LINE_BREAK_TAG_PATTERN.matcher(refreshed).replaceAll("\n"); //$NON-NLS-1$
			text = OPEN_P_TAG_PATTERN.matcher(text).replaceAll(""); //$NON-NLS-1$
			text = ANY_TAG_EXCEPT_B_PATTERN.matcher(text).replaceAll(""); //$NON-NLS-1$
			text = BOLD_TAG_PATTERN.matcher(text).replaceAll("<$1reqif-xhtml:b"); //$NON-NLS-1$
			// Only now - after every other tag has been stripped/rewritten - turn the line-break placeholders
			// into real <reqif-xhtml:br/> elements; earlier, the tag stripping would remove them again.
			// XHTML/HTML collapses bare whitespace, including newlines, when rendered.
			text = text.replace("\n", "<reqif-xhtml:br/>"); //$NON-NLS-1$ //$NON-NLS-2$
			// A tab collapses to a single space in XHTML - use non-breaking spaces (TAB_REPLACEMENT) instead.
			// stripping patterns above, so this can run at any point in the pipeline.
			text = text.replace("\t", TAB_REPLACEMENT); //$NON-NLS-1$

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			String wrapped = "<reqif-xhtml:div xmlns:reqif-xhtml=\"" + XHTML_NS + "\">" + text //$NON-NLS-1$ //$NON-NLS-2$
					+ "</reqif-xhtml:div>"; //$NON-NLS-1$
			Document parsed = builder.parse(new InputSource(new StringReader(wrapped)));
			Element importedDiv = (Element) doc.importNode(parsed.getDocumentElement(), true);

			Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-XHTML"); //$NON-NLS-1$
			Element theValue = doc.createElementNS(REQIF_NS, "THE-VALUE"); //$NON-NLS-1$
			attributeValue.appendChild(theValue);
			theValue.appendChild(importedDiv);
			Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
			attributeValue.appendChild(definition);
			appendText(doc, definition, defRefTag, defRefValue);
			return attributeValue;
		} catch (Exception e) {
			return xhtmlValue(doc, plainTextFromHtml(html, resourceSet), defRefTag, defRefValue);
		}
	}

	private static void appendText(Document doc, Element parent, String tag, String text) {
		Element child = doc.createElementNS(REQIF_NS, tag);
		child.setTextContent(text);
		parent.appendChild(child);
	}

	/** A stable identifier derived from the source model element's own XMI fragment, so re-exports keep referring to the same heading/requirement - must be a valid ReqIF/XML {@code xsd:ID} (NCName): starts with a letter or underscore, only letters/digits/{@code _.-} afterwards. */
	private static String fragmentId(EObject element) {
		String fragment = EcoreUtil.getURI(element).fragment();
		String cleaned = fragment.replaceAll("[^A-Za-z0-9_.-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
		boolean validStart = !cleaned.isEmpty() && (Character.isLetter(cleaned.charAt(0)) || cleaned.charAt(0) == '_');
		return validStart ? cleaned : "id-" + cleaned; //$NON-NLS-1$
	}

	/**
	 * Writes {@code document} plus every collected diagram image as a real
	 * {@code .reqifz} ZIP package: one {@code content.reqif} entry (the
	 * actual ReqIF XML) plus one entry per {@code imageAttachments} path -
	 * the same "XML + external resource files in a zip" shape a real
	 * DOORS/Windchill `.reqifz` export uses, confirmed by extracting one and
	 * reading its `<xhtml:object data="...">` references directly.
	 */
	private static void writeReqifZ(Document document, Map<String, byte[]> imageAttachments, File outputFile)
			throws Exception {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
		transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //$NON-NLS-1$
		transformer.setOutputProperty(OutputKeys.STANDALONE, "yes"); //$NON-NLS-1$
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //$NON-NLS-1$ //$NON-NLS-2$
		ByteArrayOutputStream reqifBytes = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(document), new StreamResult(reqifBytes));

		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
			zip.putNextEntry(new ZipEntry("content.reqif")); //$NON-NLS-1$
			zip.write(reqifBytes.toByteArray());
			zip.closeEntry();
			for (Map.Entry<String, byte[]> attachment : imageAttachments.entrySet()) {
				zip.putNextEntry(new ZipEntry(attachment.getKey()));
				zip.write(attachment.getValue());
				zip.closeEntry();
			}
		}
	}

	private static String sanitize(String name) {
		if (name == null) {
			return "requirements"; //$NON-NLS-1$
		}
		return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
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

	/** Defaults to "<repo root>/sync/requirements" when resolvable, else the user's home folder. */
	private static String guessDefaultOutputFolder(IProject project) {
		if (project != null && project.getLocation() != null) {
			File repoRoot = project.getLocation().toFile().getParentFile();
			if (repoRoot != null) {
				return new File(new File(repoRoot, "sync"), "requirements").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return project.getLocation().toOSString();
		}
		return System.getProperty("user.home"); //$NON-NLS-1$
	}
}
