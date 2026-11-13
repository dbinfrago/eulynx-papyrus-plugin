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

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gmf.runtime.diagram.core.preferences.PreferencesHint;
import org.eclipse.gmf.runtime.diagram.ui.editparts.DiagramEditPart;
import org.eclipse.gmf.runtime.diagram.ui.image.ImageFileFormat;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.diagram.ui.parts.IDiagramWorkbenchPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.papyrus.infra.core.resource.ModelSet;
import org.eclipse.papyrus.infra.core.sashwindows.di.service.IPageManager;
import org.eclipse.papyrus.infra.core.services.ServicesRegistry;
import org.eclipse.papyrus.infra.gmfdiag.menu.handlers.AbstractDiagramHandler;
import org.eclipse.papyrus.infra.ui.editor.IMultiDiagramEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Port;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Type;
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
import com.eulynx.papyrus.specification.statemachine.StateMachineRequirementSync;

/**
 * "Export Transition Information..." command: for every Block with a State
 * Machine as classifier behavior that has been run through "Sync State Machine
 * to Requirements..." ({@link StateMachineRequirementSync}), writes one combined
 * ReqIF 1.2 package ({@code .reqifz}) with one {@code SPECIFICATION} per Block.
 * <p>
 * Per Block the tree is: the Block, its Internal Block Diagram, its FlowPorts
 * (name, data type, description from the Port's first Comment) and its State
 * Machine as "Behaviour" item with the State Machine Diagram and the synced
 * state/transition Requirements below it.
 * <p>
 * Read-only: a Block that was not synced yet is skipped and reported. ReqIF
 * identifiers derive from the model elements, so a re-import updates instead of
 * duplicating.
 */
public class ExportTransitionInformationHandler extends AbstractDiagramHandler {

	private static final String REQUIREMENT_STEREOTYPE = "SysML::Requirements::Requirement"; //$NON-NLS-1$

	private static final String REQIF_NS = "http://www.omg.org/spec/ReqIF/20110401/reqif.xsd"; //$NON-NLS-1$

	private static final String XHTML_NS = "http://www.w3.org/1999/xhtml"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		IDiagramWorkbenchPart part = getDiagramWorkbenchPart();
		Diagram currentDiagram = part == null ? null : part.getDiagram();
		if (currentDiagram == null || currentDiagram.getElement() == null) {
			MessageDialog.openWarning(shell, "Export Transition Information", //$NON-NLS-1$
					"Open a diagram first, so the command knows which model to export from."); //$NON-NLS-1$
			return null;
		}

		EObject anyElement = currentDiagram.getElement();
		IProject referenceProject = resolveProject(anyElement.eResource());
		List<Class> blocks = collectBlocksWithStateMachine(anyElement.eResource().getResourceSet(), referenceProject);
		if (blocks.isEmpty()) {
			MessageDialog.openWarning(shell, "Export Transition Information", //$NON-NLS-1$
					"No Block with a State Machine classifier behavior found in this model."); //$NON-NLS-1$
			return null;
		}

		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setText("Export Transition Information"); //$NON-NLS-1$
		dialog.setFilterExtensions(new String[] { "*.reqifz" }); //$NON-NLS-1$
		dialog.setFileName("transition-information.reqifz"); //$NON-NLS-1$
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

		List<String> skippedBlocks = new ArrayList<>();
		int[] counts = new int[2]; // [requirements, blocks]
		Map<String, byte[]> imageAttachments = new LinkedHashMap<>();
		OPENED_BY_EXPORT.clear();
		try {
			Document document = buildReqIfDocument(blocks, skippedBlocks, counts, imageAttachments);
			writeReqifZ(document, imageAttachments, outputFile);
		} catch (Exception e) {
			MessageDialog.openError(shell, "Export Transition Information", //$NON-NLS-1$
					"Failed to write ReqIF file: " + e.getMessage()); //$NON-NLS-1$
			return null;
		} finally {
			closePagesOpenedByExport();
		}

		StringBuilder message = new StringBuilder();
		message.append("Exported ").append(counts[0]).append(" requirement(s) across ").append(counts[1]) //$NON-NLS-1$ //$NON-NLS-2$
				.append(" of ").append(blocks.size()).append(" block(s) to:\n").append(outputFile.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
		if (!skippedBlocks.isEmpty()) {
			message.append("\n\nSkipped (never synced via \"Sync State Machine to Requirements...\"):\n") //$NON-NLS-1$
					.append(String.join(", ", skippedBlocks)); //$NON-NLS-1$
		}
		MessageDialog.openInformation(shell, "Export Transition Information", message.toString()); //$NON-NLS-1$
		return null;
	}

	/**
	 * Builds the full ReqIF DOM: shared {@code DATATYPES}/{@code SPEC-TYPES}
	 * declared once, then one {@code SPEC-OBJECT} per Requirement and one
	 * {@code SPECIFICATION} (with a flat {@code SPEC-HIERARCHY}) per Block.
	 * {@code counts} is filled with {@code [requirementCount, blockCount]} as
	 * an out-parameter, since a DOM build has no natural single return value
	 * for both the document and these summary numbers.
	 */
	private static Document buildReqIfDocument(List<Class> blocks, List<String> skippedBlocks, int[] counts,
			Map<String, byte[]> imageAttachments) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.newDocument();

		Element reqIf = doc.createElementNS(REQIF_NS, "REQ-IF"); //$NON-NLS-1$
		doc.appendChild(reqIf);

		Element header = doc.createElementNS(REQIF_NS, "THE-HEADER"); //$NON-NLS-1$
		reqIf.appendChild(header);
		Element headerInfo = doc.createElementNS(REQIF_NS, "REQ-IF-HEADER"); //$NON-NLS-1$
		headerInfo.setAttribute("IDENTIFIER", "HEADER-eulynx-model"); //$NON-NLS-1$ //$NON-NLS-2$
		header.appendChild(headerInfo);
		appendText(doc, headerInfo, "CREATION-TIME", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); //$NON-NLS-1$
		appendText(doc, headerInfo, "REQ-IF-TOOL-ID", "com.eulynx.papyrus.specification"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "REQ-IF-VERSION", "1.2"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "SOURCE-TOOL-ID", "Papyrus"); //$NON-NLS-1$ //$NON-NLS-2$
		appendText(doc, headerInfo, "TITLE", "EULYNX State Machine Transition Information"); //$NON-NLS-1$ //$NON-NLS-2$

		Element coreContent = doc.createElementNS(REQIF_NS, "CORE-CONTENT"); //$NON-NLS-1$
		reqIf.appendChild(coreContent);
		Element content = doc.createElementNS(REQIF_NS, "REQ-IF-CONTENT"); //$NON-NLS-1$
		coreContent.appendChild(content);

		// DATATYPES + SPEC-TYPES, declared once and referenced by every SPEC-OBJECT/SPECIFICATION below.
		Element datatypes = doc.createElementNS(REQIF_NS, "DATATYPES"); //$NON-NLS-1$
		content.appendChild(datatypes);
		Element nameType = doc.createElementNS(REQIF_NS, "DATATYPE-DEFINITION-STRING"); //$NON-NLS-1$
		nameType.setAttribute("IDENTIFIER", "DATATYPE-NAME"); //$NON-NLS-1$ //$NON-NLS-2$
		nameType.setAttribute("LONG-NAME", "Name"); //$NON-NLS-1$ //$NON-NLS-2$
		nameType.setAttribute("MAX-LENGTH", "256"); //$NON-NLS-1$ //$NON-NLS-2$
		datatypes.appendChild(nameType);
		Element textType = doc.createElementNS(REQIF_NS, "DATATYPE-DEFINITION-XHTML"); //$NON-NLS-1$
		textType.setAttribute("IDENTIFIER", "DATATYPE-TEXT"); //$NON-NLS-1$ //$NON-NLS-2$
		textType.setAttribute("LONG-NAME", "Text"); //$NON-NLS-1$ //$NON-NLS-2$
		datatypes.appendChild(textType);

		Element specTypes = doc.createElementNS(REQIF_NS, "SPEC-TYPES"); //$NON-NLS-1$
		content.appendChild(specTypes);
		Element objectType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		objectType.setAttribute("IDENTIFIER", "TYPE-REQUIREMENT"); //$NON-NLS-1$ //$NON-NLS-2$
		objectType.setAttribute("LONG-NAME", "Requirement"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(objectType);
		Element specAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		objectType.appendChild(specAttributes);
		specAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		specAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-TEXT", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		// Coarse, deliberately minimal types for the Block/Diagram/Port/Behaviour work items
		// - see the class comment. Finer Polarion-side typing is meant to be worked out by hand for now.
		Element blockType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		blockType.setAttribute("IDENTIFIER", "TYPE-BLOCK"); //$NON-NLS-1$ //$NON-NLS-2$
		blockType.setAttribute("LONG-NAME", "Block"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(blockType);
		Element blockAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		blockType.appendChild(blockAttributes);
		blockAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-BLOCK-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Element diagramType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		diagramType.setAttribute("IDENTIFIER", "TYPE-DIAGRAM"); //$NON-NLS-1$ //$NON-NLS-2$
		diagramType.setAttribute("LONG-NAME", "Diagram"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(diagramType);
		Element diagramAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		diagramType.appendChild(diagramAttributes);
		diagramAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", "ATTR-DIAGRAM-NAME", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		diagramAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-XHTML", "ATTR-DIAGRAM-IMAGE", //$NON-NLS-1$ //$NON-NLS-2$
				"ReqIF.Text", "DATATYPE-DEFINITION-XHTML-REF", "DATATYPE-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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

		Element behaviourType = doc.createElementNS(REQIF_NS, "SPEC-OBJECT-TYPE"); //$NON-NLS-1$
		behaviourType.setAttribute("IDENTIFIER", "TYPE-BEHAVIOUR"); //$NON-NLS-1$ //$NON-NLS-2$
		behaviourType.setAttribute("LONG-NAME", "Behaviour"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(behaviourType);
		Element behaviourAttributes = doc.createElementNS(REQIF_NS, "SPEC-ATTRIBUTES"); //$NON-NLS-1$
		behaviourType.appendChild(behaviourAttributes);
		behaviourAttributes.appendChild(attributeDefinition(doc, "ATTRIBUTE-DEFINITION-STRING", //$NON-NLS-1$
				"ATTR-BEHAVIOUR-NAME", "ReqIF.Name", "DATATYPE-DEFINITION-STRING-REF", "DATATYPE-NAME")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		Element specificationType = doc.createElementNS(REQIF_NS, "SPECIFICATION-TYPE"); //$NON-NLS-1$
		specificationType.setAttribute("IDENTIFIER", "TYPE-MODULE"); //$NON-NLS-1$ //$NON-NLS-2$
		specificationType.setAttribute("LONG-NAME", "Module"); //$NON-NLS-1$ //$NON-NLS-2$
		specTypes.appendChild(specificationType);

		Element specObjects = doc.createElementNS(REQIF_NS, "SPEC-OBJECTS"); //$NON-NLS-1$
		content.appendChild(specObjects);
		Element specifications = doc.createElementNS(REQIF_NS, "SPECIFICATIONS"); //$NON-NLS-1$
		content.appendChild(specifications);

		int requirementCount = 0;
		int blockCount = 0;
		for (Class block : blocks) {
			StateMachine stateMachine = (StateMachine) block.getClassifierBehavior();
			Package requirementsPackage = findRequirementsPackage(stateMachine);
			if (requirementsPackage == null) {
				skippedBlocks.add(block.getName());
				continue;
			}

			ResourceSet resourceSet = block.eResource().getResourceSet();

			Element specification = doc.createElementNS(REQIF_NS, "SPECIFICATION"); //$NON-NLS-1$
			specification.setAttribute("IDENTIFIER", "SPEC-" + fragmentId(block)); //$NON-NLS-1$ //$NON-NLS-2$
			specification.setAttribute("LONG-NAME", block.getName()); //$NON-NLS-1$
			Element specType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			specification.appendChild(specType);
			appendText(doc, specType, "SPECIFICATION-TYPE-REF", "TYPE-MODULE"); //$NON-NLS-1$ //$NON-NLS-2$
			Element specificationChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
			specification.appendChild(specificationChildren);

			// (1) The Block itself - the sole top-level work item under this Block's SPECIFICATION.
			String blockId = fragmentId(block);
			Element blockObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
			blockObject.setAttribute("IDENTIFIER", "SO-" + blockId); //$NON-NLS-1$ //$NON-NLS-2$
			blockObject.setAttribute("LONG-NAME", block.getName()); //$NON-NLS-1$
			Element blockValues = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
			blockObject.appendChild(blockValues);
			blockValues.appendChild(stringValue(doc, block.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-BLOCK-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
			Element blockObjType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			blockObject.appendChild(blockObjType);
			appendText(doc, blockObjType, "SPEC-OBJECT-TYPE-REF", "TYPE-BLOCK"); //$NON-NLS-1$ //$NON-NLS-2$
			specObjects.appendChild(blockObject);

			Element blockHierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
			blockHierarchy.setAttribute("IDENTIFIER", "SH-" + blockId); //$NON-NLS-1$ //$NON-NLS-2$
			Element blockHierarchyObject = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
			blockHierarchy.appendChild(blockHierarchyObject);
			appendText(doc, blockHierarchyObject, "SPEC-OBJECT-REF", "SO-" + blockId); //$NON-NLS-1$ //$NON-NLS-2$
			Element blockChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
			blockHierarchy.appendChild(blockChildren);
			specificationChildren.appendChild(blockHierarchy);

			// (2) The Block's Internal Block Diagram, if one exists - embedded as an image.
			Diagram ibd = findDiagram(resourceSet, block, "CompositeStructure"); //$NON-NLS-1$
			if (ibd != null) {
				appendDiagramHierarchy(doc, ibd, blockChildren, specObjects, imageAttachments);
			}

			// (3) Every FlowPort of the Block, with Name/Data-Type/Description.
			for (Property attribute : block.getOwnedAttributes()) {
				if (!(attribute instanceof Port)) {
					continue;
				}
				appendPortHierarchy(doc, (Port) attribute, blockChildren, specObjects, resourceSet);
			}

			// (4) The Block's classifier-behavior StateMachine as its own "Behaviour" work item.
			String behaviourId = fragmentId(stateMachine);
			Element behaviourObject = doc.createElementNS(REQIF_NS, "SPEC-OBJECT"); //$NON-NLS-1$
			behaviourObject.setAttribute("IDENTIFIER", "SO-" + behaviourId); //$NON-NLS-1$ //$NON-NLS-2$
			behaviourObject.setAttribute("LONG-NAME", stateMachine.getName()); //$NON-NLS-1$
			Element behaviourValues = doc.createElementNS(REQIF_NS, "VALUES"); //$NON-NLS-1$
			behaviourObject.appendChild(behaviourValues);
			behaviourValues.appendChild(
					stringValue(doc, stateMachine.getName(), "ATTRIBUTE-DEFINITION-STRING-REF", "ATTR-BEHAVIOUR-NAME")); //$NON-NLS-1$ //$NON-NLS-2$
			Element behaviourObjType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
			behaviourObject.appendChild(behaviourObjType);
			appendText(doc, behaviourObjType, "SPEC-OBJECT-TYPE-REF", "TYPE-BEHAVIOUR"); //$NON-NLS-1$ //$NON-NLS-2$
			specObjects.appendChild(behaviourObject);

			Element behaviourHierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
			behaviourHierarchy.setAttribute("IDENTIFIER", "SH-" + behaviourId); //$NON-NLS-1$ //$NON-NLS-2$
			Element behaviourHierarchyObject = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
			behaviourHierarchy.appendChild(behaviourHierarchyObject);
			appendText(doc, behaviourHierarchyObject, "SPEC-OBJECT-REF", "SO-" + behaviourId); //$NON-NLS-1$ //$NON-NLS-2$
			Element behaviourChildren = doc.createElementNS(REQIF_NS, "CHILDREN"); //$NON-NLS-1$
			behaviourHierarchy.appendChild(behaviourChildren);
			blockChildren.appendChild(behaviourHierarchy);

			// (5) The StateMachine's own State Transition Diagram, if one exists - embedded as an image.
			Diagram std = findDiagram(resourceSet, stateMachine, "PapyrusUMLStateMachineDiagram"); //$NON-NLS-1$
			if (std != null) {
				appendDiagramHierarchy(doc, std, behaviourChildren, specObjects, imageAttachments);
			}

			// (6) The already-synced state/transition Requirements - unchanged, just now nested under
			// the Behaviour work item instead of directly under the Block's SPECIFICATION.
			int requirementsForBlock = 0;
			for (PackageableElement element : requirementsPackage.getPackagedElements()) {
				if (!(element instanceof Class)) {
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
				values.appendChild(boldPreservingXhtmlValue(doc, text, resourceSet, //$NON-NLS-1$
						"ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-TEXT")); //$NON-NLS-1$ //$NON-NLS-2$
				Element objType = doc.createElementNS(REQIF_NS, "TYPE"); //$NON-NLS-1$
				specObject.appendChild(objType);
				appendText(doc, objType, "SPEC-OBJECT-TYPE-REF", "TYPE-REQUIREMENT"); //$NON-NLS-1$ //$NON-NLS-2$
				specObjects.appendChild(specObject);

				Element hierarchy = doc.createElementNS(REQIF_NS, "SPEC-HIERARCHY"); //$NON-NLS-1$
				hierarchy.setAttribute("IDENTIFIER", "SH-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				Element object = doc.createElementNS(REQIF_NS, "OBJECT"); //$NON-NLS-1$
				hierarchy.appendChild(object);
				appendText(doc, object, "SPEC-OBJECT-REF", "SO-" + id); //$NON-NLS-1$ //$NON-NLS-2$
				behaviourChildren.appendChild(hierarchy);

				requirementsForBlock++;
			}
			specifications.appendChild(specification);
			requirementCount += requirementsForBlock;
			blockCount++;
		}

		counts[0] = requirementCount;
		counts[1] = blockCount;
		return doc;
	}

	/** The first {@link Diagram} view in {@code resourceSet} whose semantic element is (by identity) {@code element} and whose notation type is {@code diagramType} - {@code "CompositeStructure"} for an IBD, {@code "PapyrusUMLStateMachineDiagram"} for an STD (see {@code AddDiagramFrameHandler.guessKindPrefix}, which established these exact type strings). Identity comparison means no project-scoping is needed here, unlike other export commands - the same {@code EObject} instance cannot exist in more than one place. */
	private static Diagram findDiagram(ResourceSet resourceSet, EObject element, String diagramType) {
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".notation")) { //$NON-NLS-1$
				continue;
			}
			for (EObject content : resource.getContents()) {
				if (content instanceof Diagram && ((Diagram) content).getElement() == element
						&& diagramType.equals(((Diagram) content).getType())) {
					return (Diagram) content;
				}
			}
		}
		return null;
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

	/** Appends one "Port"-typed leaf {@code SPEC-HIERARCHY} node (Name, Data Type, and - from the port's first owned {@link Comment}, if any - a rich-text Description) to {@code parentChildren}. */
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
		if (comment != null && comment.getBody() != null && !comment.getBody().isEmpty()) {
			values.appendChild(boldPreservingXhtmlValue(doc, comment.getBody(), resourceSet, //$NON-NLS-1$
					"ATTRIBUTE-DEFINITION-XHTML-REF", "ATTR-PORT-DESCRIPTION")); //$NON-NLS-1$ //$NON-NLS-2$
		}
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

	/**
	 * Renders {@code diagram} to a throwaway temporary PNG (via the same, official
	 * GMF {@link CopyToImageUtil} {@link ExportAllDiagramsHandler} already uses for
	 * "Export All Diagrams..."), then references it via a real {@code <xhtml:object
	 * type="image/png" data="images/<id>.png">} pointing at a file inside the
	 * exported {@code .reqifz} package - the bytes are added to {@code
	 * imageAttachments} here and actually written into the zip later by {@link
	 * #writeReqifZ}. Deliberately <b>not</b> a base64 {@code data:} URI: Polarion's
	 * work-item view rendered that field completely empty on import - no error, just
	 * nothing shown - confirmed by comparing against a real reference
	 * DOORS/Windchill `.reqifz` export, which represents embedded images exactly
	 * this way (an external file inside the package, referenced from the XHTML via a
	 * relative {@code data} path) rather than inline base64, which is what fixed it.
	 * Falls back to a plain-text placeholder if rendering fails for any reason,
	 * rather than aborting the whole export over one diagram.
	 */
	private static Element diagramImageValue(Document doc, Diagram diagram, String defRefTag, String defRefValue,
			Map<String, byte[]> imageAttachments) {
		try {
			File temp = File.createTempFile("reqif-diagram-", ".svg"); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				if (diagram.eResource() != null) {
					ensurePageOpen(diagram, diagram.eResource().getResourceSet());
				}
				// See ExportRequirementsHandler.diagramImageValue: prefer the open editor's live edit part
				// (what Papyrus's own "Save as Image" uses) over the always-off-screen Diagram overload.
				DiagramEditPart live = diagram.eResource() == null ? null
						: findLiveDiagramEditPart(diagram, diagram.eResource().getResourceSet());
				if (live != null) {
					new FastSvgCopyToImageUtil().copyToImage(live, new Path(temp.getAbsolutePath()), ImageFileFormat.SVG,
							new NullProgressMonitor());
				} else {
					new FastSvgCopyToImageUtil().copyToImage(diagram, new Path(temp.getAbsolutePath()), ImageFileFormat.SVG,
							new NullProgressMonitor(), PreferencesHint.USE_DEFAULTS);
				}
				// SVG is vector, so no cropToContent here.
				byte[] bytes = Files.readAllBytes(temp.toPath());
				String relativePath = "images/" + fragmentId(diagram) + ".svg"; //$NON-NLS-1$ //$NON-NLS-2$
				imageAttachments.put(relativePath, bytes);

				Element attributeValue = doc.createElementNS(REQIF_NS, "ATTRIBUTE-VALUE-XHTML"); //$NON-NLS-1$
				Element theValue = doc.createElementNS(REQIF_NS, "THE-VALUE"); //$NON-NLS-1$
				attributeValue.appendChild(theValue);
				Element div = doc.createElementNS(XHTML_NS, "xhtml:div"); //$NON-NLS-1$
				theValue.appendChild(div);
				// A State Machine Diagram's StateMachine may carry a Comment with additional
				// information: shown above the image, in the same work item.
				if (diagram.getElement() instanceof StateMachine && diagram.eResource() != null) {
					ResourceSet diagramResources = diagram.eResource().getResourceSet();
					String intro = firstUndisplayedCommentText(diagram.getElement(), diagramResources);
					if (intro != null) {
						String[] lines = plainTextFromHtml(intro, diagramResources).split("\n"); //$NON-NLS-1$
						for (int i = 0; i < lines.length; i++) {
							if (i > 0) {
								div.appendChild(doc.createElementNS(XHTML_NS, "xhtml:br")); //$NON-NLS-1$
							}
							div.appendChild(doc.createTextNode(lines[i]));
						}
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
	 * Finds the {@link IPageManager} of whichever already-open Papyrus editor
	 * (if any) is backed by {@code resourceSet} - independently duplicated
	 * from {@code ExportRequirementsHandler}'s identical method per this
	 * plugin's convention; see that copy's own comment for the full
	 * rationale (confirmed live: an unconnected FlowPort's arrow orientation
	 * on an IBD rendered correctly via Papyrus's native "Save as Image" on
	 * an already-open diagram, but rotated 90 degrees via this exporter's
	 * own {@code CopyToImageUtil} call against the same, not-yet-open
	 * diagram). {@code CopyToImageUtil.copyToImage(...)} already prefers a
	 * Diagram's live, on-screen edit parts automatically whenever it's
	 * already open - this only needs to ensure one is. Returns
	 * {@code null} (rather than throwing) if no open editor for this model
	 * can be found, or if anything here fails - a pure best-effort
	 * improvement that never makes the existing off-screen path any worse.
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
	 * Opens {@code pageElement} (a Diagram) as a real page in whichever
	 * already-open Papyrus editor backs {@code resourceSet}, if any - see
	 * {@link #findPageManager(ResourceSet)}. Does nothing (silently) if no
	 * such editor is open, {@code pageElement} is already open, or anything
	 * here fails - the existing off-screen rendering path still runs
	 * afterward exactly as before in every one of those cases.
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

	/** Pages (pageManager, diagram) this export run opened itself - closed again afterwards; pages already open before stay open. */
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
	 * Independently duplicated from {@code ExportRequirementsHandler} per this
	 * plugin's established convention. Falls back to returning the original bytes
	 * unchanged if decoding fails, or if there's nothing worth cropping (already
	 * tight, or fully blank).
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
	 * Converts a stored rich-text {@code Comment}/{@code «Requirement».text}
	 * body (this plugin's own restricted HTML dialect - see
	 * {@code richtext.HtmlStyledTextEditor}'s class comment for the exact tag
	 * subset) into plain, unformatted text for ReqIF export.
	 * {@link RichTextReferenceUtil#refresh} first brings any reference
	 * spans' displayed names up to date, same as opening and saving the
	 * Comment/Requirement in the model would; the markup is then stripped
	 * down to its visible text (a reference span becomes just the
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
	 * Falls back to {@link #xhtmlValue(Document, String, String, String)} over
	 * {@link #plainTextFromHtml} if the parse fails for any reason.
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
			// Same underlying gap, for a literal tab typed to visually space out terms
			// (e.g. "value\tmeaning") - a plain tab character collapses to a single space
			// when rendered as XHTML/HTML, same as a bare "\n" would. A non-breaking space
			// (U+00A0, see TAB_REPLACEMENT/plainTextFromHtml) isn't collapsed this way and
			// is a literal, valid XML character - no tag-shaped text to protect from the
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
		String[] lines = (value == null ? "" : value).split("\n", -1); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				div.appendChild(doc.createElementNS(XHTML_NS, "xhtml:br")); //$NON-NLS-1$
			}
			div.appendChild(doc.createTextNode(lines[i]));
		}
		Element definition = doc.createElementNS(REQIF_NS, "DEFINITION"); //$NON-NLS-1$
		attributeValue.appendChild(definition);
		appendText(doc, definition, defRefTag, defRefValue);
		return attributeValue;
	}

	private static void appendText(Document doc, Element parent, String tag, String text) {
		Element child = doc.createElementNS(REQIF_NS, tag);
		child.setTextContent(text);
		parent.appendChild(child);
	}

	/** A stable identifier derived from the source model element's own XMI fragment, so re-exports keep referring to the same requirement - must be a valid ReqIF/XML {@code xsd:ID} (NCName): starts with a letter or underscore, only letters/digits/{@code _.-} afterwards. */
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

	/** Every UML {@link Class} (Block) whose classifier behavior is a {@link StateMachine}, restricted to {@code referenceProject} when resolvable. */
	private static List<Class> collectBlocksWithStateMachine(ResourceSet resourceSet, IProject referenceProject) {
		List<Class> result = new ArrayList<>();
		for (Resource resource : resourceSet.getResources()) {
			String lastSegment = resource.getURI().lastSegment();
			if (lastSegment == null || !lastSegment.endsWith(".uml")) { //$NON-NLS-1$
				continue;
			}
			if (referenceProject != null && !referenceProject.equals(resolveProject(resource))) {
				continue;
			}
			TreeIterator<EObject> contents = resource.getAllContents();
			while (contents.hasNext()) {
				EObject next = contents.next();
				if (next instanceof Class && ((Class) next).getClassifierBehavior() instanceof StateMachine) {
					result.add((Class) next);
				}
			}
		}
		return result;
	}

	/** Read-only counterpart to {@link StateMachineRequirementSync#resolveContainerPackage(StateMachine)} - never creates the package. */
	private static Package findRequirementsPackage(StateMachine stateMachine) {
		String packageName = stateMachine.getName() + " - Requirements"; //$NON-NLS-1$
		Package parent = stateMachine.getNearestPackage();
		if (parent == null) {
			return null;
		}
		for (PackageableElement element : parent.getPackagedElements()) {
			if (element instanceof Package && packageName.equals(element.getName())) {
				return (Package) element;
			}
		}
		return null;
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

	/** Defaults to "<repo root>/sync/transition information" when resolvable, else the user's home folder. */
	private static String guessDefaultOutputFolder(IProject project) {
		if (project != null && project.getLocation() != null) {
			File repoRoot = project.getLocation().toFile().getParentFile();
			if (repoRoot != null) {
				return new File(new File(repoRoot, "sync"), "transition information").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return project.getLocation().toOSString();
		}
		return System.getProperty("user.home"); //$NON-NLS-1$
	}
}
