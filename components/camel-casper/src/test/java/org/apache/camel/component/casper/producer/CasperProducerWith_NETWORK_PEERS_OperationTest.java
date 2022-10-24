package org.apache.camel.component.casper.producer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.peer.PeerEntry;

@SuppressWarnings("unchecked")
class CasperProducerWith_NETWORK_PEERS_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCall() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.NETWORK_PEERS);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a List
		assertTrue(body instanceof List);
		List<PeerEntry> peers = (List<PeerEntry>) (body);
		assertTrue(!peers.isEmpty());
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
