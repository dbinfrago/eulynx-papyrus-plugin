// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

/** "Show Type" command - see {@link AbstractToggleTypeHandler}. */
public class ShowTypeHandler extends AbstractToggleTypeHandler {

	@Override
	protected boolean showType() {
		return true;
	}
}
