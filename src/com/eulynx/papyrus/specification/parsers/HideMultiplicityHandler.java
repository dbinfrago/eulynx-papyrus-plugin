package com.eulynx.papyrus.specification.parsers;

/** "Hide Multiplicity" command - see {@link AbstractToggleMultiplicityHandler}. */
public class HideMultiplicityHandler extends AbstractToggleMultiplicityHandler {

	@Override
	protected boolean showMultiplicity() {
		return false;
	}
}
