package org.apache.camel.component.casper.producer;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.InvalidPathException;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

class CasperProducerComponentTest {

	private final CamelContext context = new DefaultCamelContext();

	@Test
	void testWithUnsuportedURL() throws Exception {

		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:start").to("casper:http://yyy.zzz.ww.abc.12:7777/?operation=last_block");
			}
		});
		Exception exception = assertThrows(FailedToCreateRouteException.class, () -> context.start());
		assertEquals(InvalidPathException.class, exception.getCause().getCause().getClass());
	}

	@Test
	void testWithUnsuportedOperation() throws Exception {

		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:start").to(CasperConstants.TESTNET_ENDPOINT_TEST + "?operation=test");
			}
		});

		Exception exception = assertThrows(FailedToStartRouteException.class, () -> context.start());
		assertEquals(UnsupportedOperationException.class, exception.getCause().getCause().getClass());
	}

}
