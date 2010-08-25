package org.apache.camel.itest.highvolume;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;

public class Messages {
	
	@EndpointInject(ref="Direct")
    ProducerTemplate template;
	
	@EndpointInject(ref="JmsQueueProducer")
    ProducerTemplate templateActiveMq;
	
	public void split(Exchange exchange) {
		String messages = (String) exchange.getIn().getBody();
		
		String[] result = messages.split(",");
		for (int x=0; x<result.length; x++) {
			templateActiveMq.sendBody(result[x]);
		}
	}
    
    public void generate(Exchange exchange) {
     	StringBuilder messages = new StringBuilder();
        for (int i = 1; i < 20000; i++) {
        	messages.append("Test Message: " + i + ",");
        }
        template.sendBody(messages.toString());
    }

}
