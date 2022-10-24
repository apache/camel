package org.apache.camel.component.casper.producer;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.block.JsonBlock;

class CasperProducerWith_LAST_BLOCK_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWith_DEPLOY_HASH_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.LAST_BLOCK);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a JsonBlock
		assertInstanceOf(JsonBlock.class, body);
		JsonBlock block = (JsonBlock) body;
		assertNotNull(block);
		assertEquals(64, block.getHash().length());
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
