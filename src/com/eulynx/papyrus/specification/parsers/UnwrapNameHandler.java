// SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
// SPDX-License-Identifier: Apache-2.0

package com.eulynx.papyrus.specification.parsers;

/** "Unwrap Name" command - see {@link AbstractToggleNameWrapHandler}. */
public class UnwrapNameHandler extends AbstractToggleNameWrapHandler {

	@Override
	protected boolean wrapName() {
		return false;
	}
}
