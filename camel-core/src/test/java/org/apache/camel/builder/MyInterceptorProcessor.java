/**
 * 
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MyInterceptorProcessor extends DelegateProcessor {
    private static final transient Log log = LogFactory.getLog(MyInterceptorProcessor.class);

    public void process(Exchange exchange) throws Exception {
		log.debug("START of onExchange: "+exchange);
		next.process(exchange);
		log.debug("END of onExchange: "+exchange);
	}
}