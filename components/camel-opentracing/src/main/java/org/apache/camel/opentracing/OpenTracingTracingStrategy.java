package org.apache.camel.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class OpenTracingTracingStrategy implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String UNNAMED = "unnamed";
    private final Tracer tracer;

    public OpenTracingTracingStrategy(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext camelContext,
                                                 NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {

        return new DelegateAsyncProcessor((Exchange exchange) -> {
            Span span = ActiveSpanManager.getSpan(exchange);
            if (span == null) {
                target.process(exchange);
                return;
            }

            final Span processorSpan = tracer.buildSpan(getOperationName(processorDefinition)).asChildOf(span).start();
            Tags.COMPONENT.set(processorSpan, getComponentName(processorDefinition));

            try (final Scope inScope = tracer.activateSpan(processorSpan)) {
                target.process(exchange);
            } catch (Exception ex) {
                processorSpan.log(errorLogs(ex));
                throw ex;
            } finally {
                processorSpan.finish();
            }
        });
    }
    
    private static String getComponentName(NamedNode processorDefinition) {
        return processorDefinition.getShortName();
    }

    private static String getOperationName(NamedNode processorDefinition) {
        final String name = processorDefinition.getId();
        return name == null ? UNNAMED : name;
    }

    private static Map<String, Object> errorLogs(final Throwable t) {
        final Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", t);
        return errorLogs;
    }
}
