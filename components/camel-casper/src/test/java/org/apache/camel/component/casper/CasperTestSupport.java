package org.apache.camel.component.casper;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;

public class CasperTestSupport extends CamelTestSupport {

	@EndpointInject("mock:result")
	protected MockEndpoint mockResult;

	@EndpointInject("mock:error")
	protected MockEndpoint mockError;

	@Override
	public boolean isUseAdviceWith() {
		return true;
	}

	protected String getUrl() {
		return CasperConstants.TESTNET_ENDPOINT_TEST;

	}

	protected Exchange createExchangeWithBodyAndHeader(Object body, String key, Object value) {
		DefaultExchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(body);
		exchange.getIn().setHeader(key, value);
		return exchange;
	}

	@BeforeAll
	public static void startServer() throws Exception {
	}

	@AfterAll
	public static void stopServer() throws Exception {
	}

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		super.setUp();
	}

}
