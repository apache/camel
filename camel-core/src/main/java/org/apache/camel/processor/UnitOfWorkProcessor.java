package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;

/** 
 * Handles calling the UnitOfWork.done() method when processing of an exchange
 * is complete.
 */
public final class UnitOfWorkProcessor extends DelegateAsyncProcessor {

    public UnitOfWorkProcessor(AsyncProcessor processor) {
        super(processor);
    }
    
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return processor.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                // Order here matters. We need to complete the callbacks since
                // they will likely update the exchange with some final results.
                callback.done(sync);
                exchange.getUnitOfWork().done(exchange);
            }
        });
    }

}