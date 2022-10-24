package org.apache.camel.component.casper.producer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.key.PublicKey;
import com.syntifi.casper.sdk.model.status.StatusData;

class CasperProducerWith_NODE_STATUS_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCall() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.NODE_STATUS);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a StatusData
		assertTrue(body instanceof StatusData);
		StatusData status = (StatusData) body;
		PublicKey key = PublicKey.fromTaggedHexString("017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077");
		assertTrue(Arrays.equals(status.getPublicKey().getKey(), key.getKey()));
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
