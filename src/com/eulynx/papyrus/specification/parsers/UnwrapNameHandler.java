package com.eulynx.papyrus.specification.parsers;

/** "Unwrap Name" command - see {@link AbstractToggleNameWrapHandler}. */
public class UnwrapNameHandler extends AbstractToggleNameWrapHandler {

	@Override
	protected boolean wrapName() {
		return false;
	}
}
