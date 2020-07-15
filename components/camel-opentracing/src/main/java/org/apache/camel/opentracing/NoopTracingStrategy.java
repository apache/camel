package org.apache.camel.opentracing;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class NoopTracingStrategy implements InterceptStrategy {

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext camelContext, NamedNode processorDefinition,
                                                 Processor target, Processor nextTarget) throws Exception {
        return new DelegateAsyncProcessor(target);
    }
}
