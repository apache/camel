package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.Policy;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A Delegate pattern which delegates processing to a nested AsyncProcessor which can
 * be useful for implementation inheritance when writing an {@link Policy}
 */
public class DelegateAsyncProcessor extends ServiceSupport implements AsyncProcessor {
    protected AsyncProcessor processor;

    public DelegateAsyncProcessor() {
    }
    public DelegateAsyncProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "Delegate(" + processor + ")";
    }

    public AsyncProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

}
