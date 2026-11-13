// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.timing;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gmf.runtime.common.core.service.AbstractProvider;
import org.eclipse.gmf.runtime.common.core.service.IOperation;
import org.eclipse.gmf.runtime.common.ui.services.parser.GetParserOperation;
import org.eclipse.gmf.runtime.common.ui.services.parser.IParser;
import org.eclipse.gmf.runtime.common.ui.services.parser.IParserProvider;

/**
 * Supplies {@link DurationLinkParser} for the name label of a duration link
 * that carries our operator/expression data; every other DurationConstraint
 * (e.g. one made by Papyrus itself) keeps Papyrus' own label. Registered with
 * Priority "Highest", like the transition label parser.
 */
public class DurationLinkParserProvider extends AbstractProvider implements IParserProvider {

	private final IParser parser = new DurationLinkParser();

	@Override
	public IParser getParser(IAdaptable hint) {
		return parser;
	}

	@Override
	public boolean provides(IOperation operation) {
		if (operation instanceof GetParserOperation) {
			IAdaptable hint = ((GetParserOperation) operation).getHint();
			String visualId = hint.getAdapter(String.class);
			EObject element = hint.getAdapter(EObject.class);
			return DurationLinkSupport.NAME_LABEL_TYPE.equals(visualId) && DurationLinkSupport.hasData(element);
		}
		return false;
	}
}
