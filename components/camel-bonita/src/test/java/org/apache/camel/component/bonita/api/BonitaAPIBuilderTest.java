package org.apache.camel.component.bonita.api;

import org.apache.camel.component.bonita.api.BonitaAPIBuilder;
import org.junit.Test;

public class BonitaAPIBuilderTest {

	@Test(expected=IllegalArgumentException.class)
	public void testNullBuilderInput() {
		BonitaAPIBuilder.build(null);
	}
}
