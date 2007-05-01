/**
 * 
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.processor.DelegateProcessor;

public class MyInterceptorProcessor extends DelegateProcessor {
	public void process(Exchange exchange) throws Exception {
		System.out.println("START of onExchange: "+exchange);
		next.process(exchange);
		System.out.println("END of onExchange: "+exchange);
	}
}