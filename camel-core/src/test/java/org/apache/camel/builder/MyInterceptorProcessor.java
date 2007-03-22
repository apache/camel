/**
 * 
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.processor.InterceptorProcessor;

public class MyInterceptorProcessor extends InterceptorProcessor<Exchange> {
	public void onExchange(Exchange exchange) {
		System.out.println("START of onExchange: "+exchange);
		next.onExchange(exchange);
		System.out.println("END of onExchange: "+exchange);
	}
}