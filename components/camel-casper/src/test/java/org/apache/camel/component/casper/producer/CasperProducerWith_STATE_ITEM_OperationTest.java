package org.apache.camel.component.casper.producer;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.storedvalue.StoredValue;

@SuppressWarnings("rawtypes")
class CasperProducerWith_STATE_ITEM_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWith_STATE_ROOT_HASH_KEY_Parameters() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ITEM);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH, "30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		exchange.getIn().setHeader(CasperConstants.PATH, "");
		exchange.getIn().setHeader(CasperConstants.ITEM_KEY, "hash-4dd10a0b2a7672e8ec964144634ddabb91504fe50b8461bac23584423318887d");
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a StoredValue
		assertTrue(body instanceof StoredValue);
		StoredValue value = (StoredValue) body;
		assertNotNull(value);
		// it s a contract
		assertEquals("com.syntifi.casper.sdk.model.contract.Contract", value.getValue().getClass().getName());
	}

	@Test
	void testCallWithout_KEY_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ITEM);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH, "30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		exchange.getIn().setHeader(CasperConstants.PATH, "");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "key parameter is required   with endpoint operation " + CasperConstants.STATE_ITEM;
		String actualMessage = exception.getMessage();
		// assert Exception message
		assertTrue(actualMessage.contains(expectedMessage));
		// Cause
		Object cause = exchange.getMessage().getHeader(CasperConstants.ERROR_CAUSE);
		assertTrue(cause instanceof MissingArgumentException);
	}

	@Test
	void testCallWithout_STATE_ROOT_HASH_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.STATE_ITEM);
		exchange.getIn().setHeader(CasperConstants.ITEM_KEY, "hash-4dd10a0b2a7672e8ec964144634ddabb91504fe50b8461bac23584423318887d");
		exchange.getIn().setHeader(CasperConstants.PATH, "item1,item2");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "stateRootHash parameter is required  with endpoint operation " + CasperConstants.STATE_ITEM;
		String actualMessage = exception.getMessage();
		// assert Exception message
		assertTrue(actualMessage.contains(expectedMessage));
		// Cause
		Object cause = exchange.getMessage().getHeader(CasperConstants.ERROR_CAUSE);
		assertTrue(cause instanceof MissingArgumentException);
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
