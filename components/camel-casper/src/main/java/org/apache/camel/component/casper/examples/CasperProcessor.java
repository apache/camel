package org.apache.camel.component.casper.examples;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel processor for Demo
 * 
 * @author p35862
 *
 */
public class CasperProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> map = exchange.getMessage().getHeaders();

		for (String key : map.keySet()) {
			System.out.println(key + ":" + map.get(key));
		}

	}

}
