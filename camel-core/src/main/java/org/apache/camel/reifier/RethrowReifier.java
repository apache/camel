package org.apache.camel.reifier;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RethrowDefinition;
import org.apache.camel.spi.RouteContext;

class RethrowReifier extends ProcessorReifier<RethrowDefinition> {

    RethrowReifier(ProcessorDefinition<?> definition) {
        super((RethrowDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return exchange -> {
            Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            if (e != null) {
                throw e;
            }
        };
    }

}
