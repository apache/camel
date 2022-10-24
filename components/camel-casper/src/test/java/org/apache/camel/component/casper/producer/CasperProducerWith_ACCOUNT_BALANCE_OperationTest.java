package org.apache.camel.component.casper.producer;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.casper.CasperTestSupport;
import org.apache.commons.cli.MissingArgumentException;
import org.junit.jupiter.api.Test;

import com.syntifi.casper.sdk.model.balance.BalanceData;

class CasperProducerWith_ACCOUNT_BALANCE_OperationTest extends CasperTestSupport {
	@Produce("direct:start")
	protected ProducerTemplate template;

	@Override
	public boolean isUseAdviceWith() {
		return false;
	}


	@Test
	void testCallWith_STATE_ROOT_HASH_KEY_Parameters() throws Exception {

		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.ACCOUNT_BALANCE);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH,
				"30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		exchange.getIn().setHeader(CasperConstants.PURSE_UREF,
				"uref-9cC68775d07c211e44068D5dCc2cC28A67Cb582C3e239E83Bb0c3d067C4D0363-007");
		template.send(exchange);
		Object body = exchange.getIn().getBody();
		// assert Object is a BalanceData
		assertTrue(body instanceof BalanceData);
		BalanceData balance = (BalanceData) body;
		assertNotNull(balance);
		//assert balance value
		assertEquals(new BigInteger("869077209920") , balance.getValue());
	}


	@Test
	 void testCallWithout_UREF_PURSE_KEY_Parameter() throws Exception {

		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.ACCOUNT_BALANCE);
		exchange.getIn().setHeader(CasperConstants.STATE_ROOT_HASH,
				"30cE5146268305AeeFdCC05a5f7bE7aa6dAF187937Eed9BB55Af90e1D49B7956");
		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "purseUref parameter is required   with endpoint operation " + CasperConstants.ACCOUNT_BALANCE;
		String actualMessage = exception.getMessage();

		// assert Exception message
		assertTrue(actualMessage.contains(expectedMessage));
		// Cause
		Object cause = exchange.getMessage().getHeader(CasperConstants.ERROR_CAUSE);
		assertTrue(cause instanceof MissingArgumentException);
	}


	@Test
	 void testCallWithout_STATE_ROOT_HASH_Parameter() throws Exception {

		Exchange exchange = createExchangeWithBodyAndHeader(null, CasperConstants.OPERATION, CasperConstants.ACCOUNT_BALANCE);
		exchange.getIn().setHeader(CasperConstants.PURSE_UREF,
				"uref-9cC68775d07c211e44068D5dCc2cC28A67Cb582C3e239E83Bb0c3d067C4D0363-007");

		template.send(exchange);
		Exception exception = exchange.getException();
		assertTrue(exception instanceof CamelExchangeException);
		String expectedMessage = "stateRootHash parameter is required   with endpoint operation " + CasperConstants.ACCOUNT_BALANCE;
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