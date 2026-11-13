// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

/** "Hide Multiplicity" command - see {@link AbstractToggleMultiplicityHandler}. */
public class HideMultiplicityHandler extends AbstractToggleMultiplicityHandler {

	@Override
	protected boolean showMultiplicity() {
		return false;
	}
}
