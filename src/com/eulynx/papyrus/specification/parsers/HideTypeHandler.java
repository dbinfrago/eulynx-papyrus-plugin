package com.eulynx.papyrus.specification.parsers;

/** "Hide Type" command - see {@link AbstractToggleTypeHandler}. */
public class HideTypeHandler extends AbstractToggleTypeHandler {

	@Override
	protected boolean showType() {
		return false;
	}
}
