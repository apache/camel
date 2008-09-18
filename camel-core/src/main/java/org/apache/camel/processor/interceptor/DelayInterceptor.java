package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.processor.DelayProcessorSupport;

/**
 * An interceptor for delaying routes.
 */
public class DelayInterceptor extends DelayProcessorSupport {

    private final ProcessorType node;
    private Delayer delayer;

    public DelayInterceptor(ProcessorType node, Processor target, Delayer delayer) {
        super(target);
        this.node = node;
        this.delayer = delayer;
    }

    @Override
    public String toString() {
        return "DelayInterceptor[delay: " + delayer.getDelay() + " on: " + node + "]";
    }

    public void delay(Exchange exchange) throws Exception {
        if (delayer.isEnabled()) {
            long time = currentSystemTime() + delayer.getDelay();
            waitUntil(time, exchange);
        }
    }

}
