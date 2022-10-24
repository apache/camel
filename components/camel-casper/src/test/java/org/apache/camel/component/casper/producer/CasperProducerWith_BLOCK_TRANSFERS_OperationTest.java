package org.apache.camel.component.casper.producer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.deploy.executabledeploy.Transfer;

@SuppressWarnings("unchecked")
class CasperProducerWith_BLOCK_TRANSFERS_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWithout_parameters() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.BLOCK_TRANSFERS);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a List
		assertInstanceOf(List.class,body);
		
	}

	@Test
	void testCallWith_BLOCK_HASH_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.BLOCK_TRANSFERS);
		exchange.getIn().setHeader(CasperConstants.BLOCK_HASH, "d162d54f93364bb9cafd923cfde35a195e6a76d2f67515ddb2dce12443dc8aa5");
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a List
		assertInstanceOf(List.class, body);
		List<Transfer> transferts = (List<Transfer>) (body);
		assertTrue(transferts.isEmpty());
	}

	@Test
	void testCallWith_BLOCK_HEIGHT_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.BLOCK_TRANSFERS);
		exchange.getIn().setHeader(CasperConstants.BLOCK_HEIGHT, 534838);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a List
		assertInstanceOf(List.class, body);
		List<Transfer> transferts = (List<Transfer>) (body);
		assertTrue(!transferts.isEmpty());
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			public void configure() {
				from("direct:start").to(getUrl());

			}
		};
	}

}
