package org.apache.camel.component.casper.examples;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel processor for Demo
 * 
 * @author p35862
 *
 */
public class CasperProcessor implements Processor {
	public static final Logger LOG = LoggerFactory.getLogger(CasperProcessor.class);

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> map = exchange.getMessage().getHeaders();

		for (String key : map.keySet()) {
			LOG.info(key + ":" + map.get(key));
		}

	}

}
