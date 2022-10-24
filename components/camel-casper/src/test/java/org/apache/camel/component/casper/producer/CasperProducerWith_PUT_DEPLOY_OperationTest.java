package org.apache.camel.component.casper.producer;

import java.net.URI;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.deploy.Deploy;
import com.syntifi.casper.sdk.service.CasperService;

class CasperProducerWith_PUT_DEPLOY_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;
	private CasperService casperService;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWith_DEPLOY_Parameter() throws Exception {
		Deploy deploy = new Deploy();
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.PUT_DEPLOY);
		exchange.getIn().setHeader(CasperConstants.DEPLOY, deploy);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a DeployResult
	}

	@Test
	void testCallWithout_DEPLOY_Parameter() throws Exception {
		Deploy deploy = new Deploy();
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.PUT_DEPLOY);
		exchange.getIn().setHeader(CasperConstants.DEPLOY, deploy);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a DeployResult
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			public void configure() {
				from("direct:start").to(getUrl());

			}
		};
	}

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		URI uri = new URI(CasperConstants.TESTNET_NODE_URL);
		casperService = CasperService.usingPeer(uri.getHost(), uri.getPort());
		super.setUp();
	}
}