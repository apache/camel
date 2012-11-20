package org.apache.camel.component.spring.ws.testfilters;

import org.apache.camel.Exchange;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.springframework.ws.WebServiceMessage;

public class EmptyMessageFilter implements MessageFilter {

	@Override
	public void filterProducer(Exchange exchange,
			WebServiceMessage produceResponse) {
		// Do nothing

	}

	@Override
	public void filterConsumer(Exchange exchange,
			WebServiceMessage consumerResponse) {
		// Do nothing

	}

}
