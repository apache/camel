package org.apache.camel.reifier.errorhandler;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.spi.RouteContext;

public class ErrorHandlerRefReifier extends ErrorHandlerReifier<ErrorHandlerBuilderRef> {

    public ErrorHandlerRefReifier(ErrorHandlerFactory definition) {
        super((ErrorHandlerBuilderRef) definition);
    }

    @Override
    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        return definition.createErrorHandler(routeContext, processor);
    }

}
