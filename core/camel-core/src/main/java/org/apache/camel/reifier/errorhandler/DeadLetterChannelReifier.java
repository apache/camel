package org.apache.camel.reifier.errorhandler;

import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.StringHelper;

public class DeadLetterChannelReifier extends DefaultErrorHandlerReifier<DeadLetterChannelBuilder> {

    public DeadLetterChannelReifier(ErrorHandlerFactory definition) {
        super((DeadLetterChannelBuilder) definition);
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        validateDeadLetterUri(routeContext);

        DeadLetterChannel answer = new DeadLetterChannel(routeContext.getCamelContext(), processor,
                definition.getLogger(), definition.getOnRedelivery(),
                definition.getRedeliveryPolicy(), definition.getExceptionPolicyStrategy(),
                definition.getFailureProcessor(), definition.getDeadLetterUri(),
                definition.isDeadLetterHandleNewException(), definition.isUseOriginalMessage(),
                definition.getRetryWhilePolicy(routeContext.getCamelContext()),
                getExecutorService(routeContext.getCamelContext()),
                definition.getOnPrepareFailure(), definition.getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    protected void validateDeadLetterUri(RouteContext routeContext) {
        Endpoint deadLetter = definition.getDeadLetter();
        String deadLetterUri = definition.getDeadLetterUri();
        if (deadLetter == null) {
            StringHelper.notEmpty(deadLetterUri, "deadLetterUri", this);
            deadLetter = routeContext.getCamelContext().getEndpoint(deadLetterUri);
            if (deadLetter == null) {
                throw new NoSuchEndpointException(deadLetterUri);
            }
            // TODO: ErrorHandler: no modification to the model should be done
            definition.setDeadLetter(deadLetter);
        }
    }

}
