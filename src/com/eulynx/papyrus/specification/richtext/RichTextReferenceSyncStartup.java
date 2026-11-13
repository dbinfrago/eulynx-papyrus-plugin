package com.eulynx.papyrus.specification.richtext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.papyrus.infra.core.services.ServicesRegistry;
import org.eclipse.papyrus.infra.ui.editor.IMultiDiagramEditor;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Registers a {@link RichTextReferenceSync} listener on every Papyrus
 * model's editing domain as its editor is opened, so automatic rich-text
 * reference refreshing (see {@link RichTextReferenceSync}) is active for the
 * whole workbench session with no per-model setup step needed.
 * <p>
 * Hooks every already-open editor at startup, then every subsequently
 * opened/activated part via {@link IPartListener2} (an editor's
 * {@link ServicesRegistry} - and with it its editing domain - is not
 * necessarily available the instant it's constructed, so re-checking on
 * {@code partActivated} too, not just {@code partOpened}, is what makes this
 * reliable rather than racy).
 */
public class RichTextReferenceSyncStartup implements IStartup {

	private static final Set<TransactionalEditingDomain> REGISTERED = Collections
			.newSetFromMap(new IdentityHashMap<>());

	@Override
	public void earlyStartup() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				hookWindow(window);
			}
			PlatformUI.getWorkbench().addWindowListener(new IWindowListener() {
				@Override
				public void windowOpened(IWorkbenchWindow window) {
					hookWindow(window);
				}

				@Override
				public void windowActivated(IWorkbenchWindow window) {
				}

				@Override
				public void windowDeactivated(IWorkbenchWindow window) {
				}

				@Override
				public void windowClosed(IWorkbenchWindow window) {
				}
			});
		});
	}

	private void hookWindow(IWorkbenchWindow window) {
		if (window == null) {
			return;
		}
		for (IWorkbenchPage page : window.getPages()) {
			for (IEditorReference reference : page.getEditorReferences()) {
				registerFor(reference.getEditor(false));
			}
		}
		window.getPartService().addPartListener(new IPartListener2() {
			@Override
			public void partOpened(IWorkbenchPartReference partRef) {
				registerFor(partRef.getPart(false));
			}

			@Override
			public void partActivated(IWorkbenchPartReference partRef) {
				registerFor(partRef.getPart(false));
			}

			@Override
			public void partBroughtToTop(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partClosed(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partDeactivated(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partHidden(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partVisible(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partInputChanged(IWorkbenchPartReference partRef) {
			}
		});
	}

	private void registerFor(Object part) {
		if (!(part instanceof IMultiDiagramEditor)) {
			return;
		}
		try {
			ServicesRegistry registry = ((IMultiDiagramEditor) part).getServicesRegistry();
			TransactionalEditingDomain domain = registry.getService(TransactionalEditingDomain.class);
			if (domain != null && REGISTERED.add(domain)) {
				domain.addResourceSetListener(new RichTextReferenceSync());
			}
		} catch (Exception e) {
			// Editor not fully initialized yet (ServicesRegistry not populated) - a
			// later partActivated event retries, so this is safe to swallow.
		}
	}
}
