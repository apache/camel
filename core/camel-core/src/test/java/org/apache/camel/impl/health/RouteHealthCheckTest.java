package org.apache.camel.impl.health;

import java.util.Collections;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.junit.Test;

public class RouteHealthCheckTest extends ContextTestSupport {

	private static final String TEST_ROUTE_ID = "Test-Route";

	@Test
	public void testDoCallDoesNotHaveNPEWhenJmxDisabled() {
		Route route = context.getRoute(TEST_ROUTE_ID);

		RouteHealthCheck healthCheck = new RouteHealthCheck(route);
		final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(healthCheck);

		healthCheck.doCall(builder, Collections.emptyMap());
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:input").id(TEST_ROUTE_ID).log("Message");
			}
		};
	}

}
