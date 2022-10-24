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

import com.syntifi.casper.sdk.model.clvalue.CLValueString;
import com.syntifi.casper.sdk.model.dictionary.DictionaryData;

class CasperProducerWith_STATE_DICTIONARY_ITEM_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}

	@Test
	void testCallWith_All_Parameters() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.DICTIONARY_ITEM);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH, "8180307A39A8583a4a164154C360FB9Ab9B15A5B626295635A62DFc7A82e66a3");
		exchange.getIn().setHeader(CasperConstants.DICTIONARY_ITEM_KEY, "a8261377ef9cf8e741dd6858801c71e38c9322e66355586549b75ab24bdd73f2");
		exchange.getIn().setHeader(CasperConstants.SEED_UREF, "uref-F5ea525E6493B41DC3c9b196ab372b6F3f00cA6F1EEf8fe0544e7d044E5480Ba-007");
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		assertTrue(body instanceof DictionaryData);
		DictionaryData data = (DictionaryData) body;
		assertNotNull(data);
		// it s a String CLValue
		assertEquals("com.syntifi.casper.sdk.model.clvalue.CLValueString", data.getStoredValue().getValue().getClass().getName());
		CLValueString value = (CLValueString) data.getStoredValue().getValue();
		assertEquals("https://caspercommunity.io", value.getParsed());
	}

	@Test
	void testCallWithout_DICTIONNARY_KEY_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.DICTIONARY_ITEM);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH, "30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		exchange.getIn().setHeader(CasperConstants.DICTIONARY_ITEM_KEY, "");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "dictionnary key parameter is required   with endpoint operation " + CasperConstants.DICTIONARY_ITEM;
		String actualMessage = exception.getMessage();
		// assert Exception message
		assertTrue(actualMessage.contains(expectedMessage));
		// Cause
		Object cause = exchange.getMessage().getHeader(CasperConstants.ERROR_CAUSE);
		assertTrue(cause instanceof MissingArgumentException);
	}

	@Test
	void testCallWithout_STATE_ROOT_HASH_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.DICTIONARY_ITEM);
		exchange.getIn().setHeader(CasperConstants.DICTIONARY_ITEM_KEY, "a8261377ef9cf8e741dd6858801c71e38c9322e66355586549b75ab24bdd73f2");
		exchange.getIn().setHeader(CasperConstants.SEED_UREF, "a8261377ef9cf8e741dd6858801c71e38c9322e66355586549b75ab24bdd73f2");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "stateRootHash parameter is required  with endpoint operation " + CasperConstants.DICTIONARY_ITEM;
		String actualMessage = exception.getMessage();
		// assert Exception message
		assertTrue(actualMessage.contains(expectedMessage));
		// Cause
		Object cause = exchange.getMessage().getHeader(CasperConstants.ERROR_CAUSE);
		assertTrue(cause instanceof MissingArgumentException);
	}

	@Test
	void testCallWithout_SEED_UREF_Parameter() throws Exception {
		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.DICTIONARY_ITEM);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH, "30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		exchange.getIn().setHeader(CasperConstants.DICTIONARY_ITEM_KEY, "a8261377ef9cf8e741dd6858801c71e38c9322e66355586549b75ab24bdd73f2");
		exchange.getIn().setHeader(CasperConstants.SEED_UREF, "");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "seedUref parameter is required   with endpoint operation " + CasperConstants.DICTIONARY_ITEM;
		String actualMessage = exception.getMessage();
		// assert Exception message
		System.out.println(actualMessage);
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
