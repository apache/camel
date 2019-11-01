package org.apache.camel.component.cxf;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.apache.cxf.message.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(classes = CxfComponentEnableMtomTest.TestConfig.class)
@MockEndpoints
public class CxfComponentEnableMtomTest {

	@Autowired
	private CamelContext context;

	@Test
	public void testIsMtomEnabled_EnabledThroughBeanSetter() throws InterruptedException {
		Endpoint endpoint = context.getEndpoint("cxf:bean:mtomByBeanSetter");

		if (endpoint instanceof CxfEndpoint) {
			CxfEndpoint cxfEndpoint = (CxfEndpoint)  endpoint;
			assertTrue("Mtom should be enabled", cxfEndpoint.isMtomEnabled());
		} else {
			fail("CXF Endpoint not found");
		}
	}

	@Test
	public void testIsMtomEnabled_EnabledThroughBeanProperties() throws InterruptedException {
		Endpoint endpoint = context.getEndpoint("cxf:bean:mtomByBeanProperties");

		if (endpoint instanceof CxfEndpoint) {
			CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
			assertTrue("Mtom should be enabled", cxfEndpoint.isMtomEnabled());
		} else {
			fail("CXF Endpoint not found");
		}
	}

	@Test
	public void testIsMtomEnabled_EnabledThroughURIProperties() throws InterruptedException {
		Endpoint endpoint = context.getEndpoint("cxf:bean:mtomByURIProperties?properties.mtom-enabled=true");

		if (endpoint instanceof CxfEndpoint) {
			CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
			assertTrue("Mtom should be enabled", cxfEndpoint.isMtomEnabled());
		} else {
			fail("CXF Endpoint not found");
		}
	}

	@Test
	public void testIsMtomEnabled_EnabledThroughQueryParameters() throws InterruptedException {
		Endpoint endpoint = context.getEndpoint("cxf:bean:mtomByQueryParameters?mtomEnabled=true");

		if (endpoint instanceof CxfEndpoint) {
			CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
			assertTrue("Mtom should be enabled", cxfEndpoint.isMtomEnabled());
		} else {
			fail("CXF Endpoint not found");
		}
	}

	@Configuration
	static class TestConfig {

		@Bean
		public CamelContext context() {			
			return new SpringCamelContext();
		}
		
		@Bean("mtomByQueryParameters")
		public CxfEndpoint mtomByQueryParameters(CamelContext context) {
			CxfEndpoint endpoint = new CxfEndpoint();
			endpoint.setCamelContext(context);
			return endpoint;
		}

		@Bean("mtomByURIProperties")
		public CxfEndpoint mtomByURIProperties() {
			return new CxfEndpoint();
		}

		@Bean("mtomByBeanProperties")
		public CxfEndpoint mtomByBeanProperties() {
			CxfEndpoint endpoint = new CxfEndpoint();
			Map<String, Object> properties = new HashMap<>();
			properties.put(Message.MTOM_ENABLED, true);

			endpoint.setProperties(properties);
			return endpoint;

		}

		@Bean("mtomByBeanSetter")
		public CxfEndpoint mtomByBeanSetter() {
			CxfEndpoint endpoint = new CxfEndpoint();
			endpoint.setMtomEnabled(true);
			return endpoint;

		}
	}
}
