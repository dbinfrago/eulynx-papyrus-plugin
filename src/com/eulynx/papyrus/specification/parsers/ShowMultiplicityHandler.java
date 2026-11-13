package com.eulynx.papyrus.specification.parsers;

/** "Show Multiplicity" command - see {@link AbstractToggleMultiplicityHandler}. */
public class ShowMultiplicityHandler extends AbstractToggleMultiplicityHandler {

	@Override
	protected boolean showMultiplicity() {
		return true;
	}
}
