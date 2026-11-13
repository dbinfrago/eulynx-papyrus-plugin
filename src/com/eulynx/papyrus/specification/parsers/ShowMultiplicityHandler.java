// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

/** "Show Multiplicity" command - see {@link AbstractToggleMultiplicityHandler}. */
public class ShowMultiplicityHandler extends AbstractToggleMultiplicityHandler {

	@Override
	protected boolean showMultiplicity() {
		return true;
	}
}
