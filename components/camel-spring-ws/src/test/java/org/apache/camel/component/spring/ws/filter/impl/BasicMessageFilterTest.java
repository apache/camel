package org.apache.camel.component.spring.ws.filter.impl;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;

import org.apache.camel.component.spring.ws.SpringWebserviceConstants;
import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.fest.assertions.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.ws.pox.dom.DomPoxMessage;
import org.springframework.ws.pox.dom.DomPoxMessageFactory;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;

@RunWith(value = JUnit4.class)
public class BasicMessageFilterTest extends ExchangeTestSupport {

	private BasicMessageFilter filter;
	private SoapMessage message;

	@Before
	public void Before() {
		filter = new BasicMessageFilter();
		SaajSoapMessageFactory saajSoapMessageFactory = new SaajSoapMessageFactory();
		saajSoapMessageFactory.afterPropertiesSet();
		message = saajSoapMessageFactory.createWebServiceMessage();
	}

	@Test
	public void testNulls() throws Exception {
		filter.filterConsumer(null, null);
		filter.filterProducer(null, null);
	}

	@Test
	public void testNullsWithExchange() throws Exception {
		filter.filterConsumer(exchange, null);
		filter.filterProducer(exchange, null);
	}

	@Test
	public void NonSoapMessageShouldBeSkipped() throws Exception {
		DomPoxMessage domPoxMessage = new DomPoxMessageFactory()
				.createWebServiceMessage();
		filter.filterConsumer(exchange, domPoxMessage);
		filter.filterProducer(exchange, domPoxMessage);

	}

	@Test
	public void withoutHeader() throws Exception {
		exchange.getIn().getHeaders().clear();
		exchange.getOut().getHeaders().clear();

		exchange.getIn().getAttachments().clear();
		exchange.getOut().getAttachments().clear();

		filter.filterProducer(exchange, message);
		filter.filterConsumer(exchange, message);

		Assertions.assertThat(message.getAttachments()).isEmpty();
		Assertions.assertThat(message.getSoapHeader().examineAllHeaderElements()).isEmpty();

		Assertions.assertThat(message.getSoapHeader().getAllAttributes()).isEmpty();
	}
	

	@Test
	public void removeCamelInternalHeaderAttributes() throws Exception {
		exchange.getOut()
				.getHeaders()
				.put(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION,
						"mustBeRemoved");
		exchange.getOut()
				.getHeaders()
				.put(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION,
						"mustBeRemoved");
		exchange.getOut()
				.getHeaders()
				.put(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI,
						"mustBeRemoved");

		exchange.getOut().getHeaders().put("breadcrumbId", "mustBeRemoved");

		filter.filterConsumer(exchange, message);

		Assertions.assertThat(message.getAttachments()).isEmpty();
		Assertions.assertThat(message.getSoapHeader().examineAllHeaderElements()).isEmpty();

		Assertions.assertThat(message.getSoapHeader().getAllAttributes()).isEmpty();
	}

	@Test
	public void consumer_withHeader() throws Exception {
		exchange.getOut().getHeaders()
				.put("headerAttributeKey", "testAttributeValue");
		exchange.getOut()
				.getHeaders()
				.put("headerAttributeElement",
						new QName("http://shouldBeInHeader", "<myElement />"));
		filter.filterConsumer(exchange, message);

		Assertions.assertThat(message.getAttachments()).isEmpty();

		Assertions.assertThat(message.getSoapHeader().examineAllHeaderElements())
		.isNotEmpty().hasSize(1);

		Assertions.assertThat(message.getSoapHeader().getAllAttributes())
		.isNotEmpty().hasSize(1);

	}

	@Test
	public void producer_withHeader() throws Exception {
		// foo is already in the header.in from the parent ExchangeTestSupport
		exchange.getIn().getHeaders()
				.put("headerAttributeKey", "testAttributeValue");
		exchange.getIn()
				.getHeaders()
				.put("headerAttributeElement",
						new QName("http://shouldBeInHeader", "<myElement />"));

		filter.filterProducer(exchange, message);

		Assertions.assertThat(message.getAttachments()).isEmpty();

		Assertions.assertThat(message.getSoapHeader().examineAllHeaderElements())
		.isNotEmpty().hasSize(1);

		Assertions.assertThat(message.getSoapHeader().getAllAttributes())
		.isNotEmpty().hasSize(2);

	}

	@Test
	public void withoutAttachment() throws Exception {
		filter.filterConsumer(exchange, message);
		filter.filterProducer(exchange, message);

		Assertions.assertThat(message.getAttachments()).isEmpty();
	}

	@Test
	public void producer_withAttachment() throws Exception {
		exchange.getIn().addAttachment(	"testAttachment",
				new DataHandler(this.getClass().getResource("/sampleAttachment.txt")));
		
		filter.filterProducer(exchange, message);
		
		Assertions.assertThat(message.getAttachments()).isNotNull().isNotEmpty();
		Assertions.assertThat(message.getAttachment("testAttachment")).isNotNull();
	}

	@Test
	public void consumer_withAttachment() throws Exception {
		exchange.getOut().addAttachment(	"testAttachment",
				new DataHandler(this.getClass().getResource("/sampleAttachment.txt")));
		
		filter.filterConsumer(exchange, message);
		
		Assertions.assertThat(message.getAttachments()).isNotNull().isNotEmpty();
		Assertions.assertThat(message.getAttachment("testAttachment")).isNotNull();
	}
}
