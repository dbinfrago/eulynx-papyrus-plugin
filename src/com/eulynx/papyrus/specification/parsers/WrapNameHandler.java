// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

/** "Wrap Name" command - see {@link AbstractToggleNameWrapHandler}. */
public class WrapNameHandler extends AbstractToggleNameWrapHandler {

	@Override
	protected boolean wrapName() {
		return true;
	}
}
