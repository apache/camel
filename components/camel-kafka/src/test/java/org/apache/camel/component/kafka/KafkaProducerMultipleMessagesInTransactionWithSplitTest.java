package org.apache.camel.component.kafka;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class KafkaProducerMultipleMessagesInTransactionWithSplitTest extends CamelTestSupport {
	@EndpointInject("mock:done")
	protected MockEndpoint doneEndpoint;
	
	private MockProducer<String, String> mockProducer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
			
	@Override
	protected CamelContext createCamelContext() throws Exception {
		CamelContext context = super.createCamelContext();
		
		KafkaClientFactory kcf = Mockito.mock(KafkaClientFactory.class);
		Mockito.when(kcf.getProducer(any(Properties.class))).thenReturn(mockProducer);
		
		KafkaComponent kafka = new KafkaComponent(); 
	    kafka.getConfiguration().setBrokers("broker1:1234,broker2:4567");
	    kafka.getConfiguration().setRecordMetadata(true);
	    kafka.setKafkaClientFactory(kcf);
	    		
		context.addComponent("kafka", kafka);
		
		return context;
	}

	/**
	 * In a Split EIP sends messages with transactional.id to Kafka.
	 */
	@Test
	public void test01_HappySplitPath() throws Exception {
		StringBuilder sb = new StringBuilder();
		int messageCount = 5;
	
		doneEndpoint.expectedMessageCount(1);
	
		for (int i = 0; i < messageCount; i++) {
			sb.append(String.format("test%02d\n", i + 1));
		}
		
		template.sendBody("direct:split", sb.toString());			
	
		MockEndpoint.assertIsSatisfied(context);
	
		assertEquals(messageCount, mockProducer.history().size());
		assertEquals(1, mockProducer.commitCount());
	}

	/**
	 * Using the same route as in test01_HappySplitPath but will throw a
	 * RuntimeException on the last iteration.
	 */
	@Test
	public void test02_OnExceptionWithSplitPath() throws Exception {
		StringBuilder sb = new StringBuilder();
		Exception exceptionCaught = null;
		int throwExeptionOnIndex = 4;
		
		for (int i = 0; i < throwExeptionOnIndex+1; i++) {
			sb.append(String.format("test%02d\n", i + 1));
		}
		
		try {
			template.sendBodyAndHeader("direct:split", sb.toString(), "ThrowExeptionOnIndex", throwExeptionOnIndex);			
		} catch (CamelExecutionException e) {
			exceptionCaught = e;
		}
	
		assertNotNull(exceptionCaught);
		assertInstanceOf(CamelExchangeException.class, exceptionCaught.getCause());
		assertInstanceOf(RuntimeException.class, exceptionCaught.getCause().getCause());
		assertEquals("Failing with Index: "+throwExeptionOnIndex, exceptionCaught.getCause().getCause().getMessage());
	
		assertEquals(0, mockProducer.history().size());
		assertEquals(0, mockProducer.commitCount());
	}

	@Override
	protected RoutesBuilder createRouteBuilder() throws Exception {
				
		return new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:split")
					.id("split")
					.setVariable("ThrowExeptionOnIndex", 
							header("ThrowExeptionOnIndex").convertTo(Integer.class))
					.split(body().tokenize("\n")).shareUnitOfWork(true).stopOnException()
						.choice().when(exchange -> {
							Integer throwExeptionOnIndex = exchange.getVariable("ThrowExeptionOnIndex", Integer.class);
							Integer camelSplitIndex = exchange.getProperty("CamelSplitIndex", Integer.class);
	
							if (null != throwExeptionOnIndex && throwExeptionOnIndex == camelSplitIndex) {
								return true;
							} else {
								System.out.printf("***** Sending message to Kafka from Split exchange with id '%s' and UnitOfWork: %s%n",
									exchange.getExchangeId(), exchange.getUnitOfWork().hashCode());
	
								return false;
							}
						})
							.throwException(RuntimeException.class, "Failing with Index: ${exchangeProperty.CamelSplitIndex}")
						.otherwise()
							.to("kafka:split?additional-properties[transactional.id]=45678&additional-properties[enable.idempotence]=true&additional-properties[retries]=5")
						.end() // .choice
					.end() // .split
					.to("mock:done");
			}
		};
	}
}
