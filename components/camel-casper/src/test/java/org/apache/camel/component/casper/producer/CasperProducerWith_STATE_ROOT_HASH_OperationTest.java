package org.apache.camel.component.casper.producer;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.stateroothash.StateRootHashData;
import com.syntifi.casper.sdk.service.CasperService;

class CasperProducerWith_STATE_ROOT_HASH_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;
	private CasperService casperService;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWith_BLOCK_HASH_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ROOT_HASH);
		exchange.getIn().setHeader(CasperConstants.BLOCK_HASH, "30c1263cbcc95066f5c20e96cb8ba11356295515f414961b646e831c17992d26");
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a StateRootHashData
		assertInstanceOf(StateRootHashData.class, body) ;
		StateRootHashData stateRootHashData = (StateRootHashData) body;
		assertNotNull(stateRootHashData);
		assertEquals("6ed7d4fb578dbb0f7eedbeff3476b8232c58bf20b7186b3a96623f15488af012", stateRootHashData.getStateRootHash().toLowerCase());
	}

	@Test
	void testCallWith_BLOCK_HEIGHT_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ROOT_HASH);
		exchange.getIn().setHeader(CasperConstants.BLOCK_HEIGHT, 535344);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a StateRootHashData
		assertInstanceOf(StateRootHashData.class, body) ;
		StateRootHashData stateRootHashData = (StateRootHashData) body;
		assertNotNull(stateRootHashData);
		assertEquals("35774f03e8d0f1ff591c5ce41ab5bfaabba196728419d0a009273751d6f9637e", stateRootHashData.getStateRootHash().toLowerCase());
	}

	@Test
	void testCallWithout_Parameters() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ROOT_HASH);
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a StateRootHashData
		assertInstanceOf(StateRootHashData.class, body) ;
		// Assert the call returns the last StateRootHash
		assertEquals(casperService.getStateRootHash().getStateRootHash().toLowerCase(), ((StateRootHashData) body).getStateRootHash().toLowerCase());
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