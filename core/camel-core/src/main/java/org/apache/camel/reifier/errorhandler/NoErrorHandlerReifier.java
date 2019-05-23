package org.apache.camel.reifier.errorhandler;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class NoErrorHandlerReifier extends ErrorHandlerReifier<NoErrorHandlerBuilder> {

    public NoErrorHandlerReifier(ErrorHandlerFactory definition) {
        super((NoErrorHandlerBuilder) definition);
    }

    @Override
    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        return new DelegateAsyncProcessor(processor) {
            @Override
            public boolean process(final Exchange exchange, final AsyncCallback callback) {
                return super.process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);
                        callback.done(doneSync);
                    }
                });
            }

            @Override
            public String toString() {
                if (processor == null) {
                    // if no output then dont do any description
                    return "";
                }
                return "NoErrorHandler[" + processor + "]";
            }
        };
    }
}
