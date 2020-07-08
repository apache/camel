package org.apache.camel.opentracing;

import io.opentracing.Span;
import io.opentracing.Tracer;
import org.apache.camel.Exchange;

public class CustomSpanAdder {

    public void addSpan(Exchange exchange) {
        Tracer tracer = (Tracer)exchange.getContext().getRegistry().lookupByName("mockTracer");
        Span customSpan = tracer.buildSpan("customSpan").start();
        customSpan.setTag("component", "camel-direct");
        customSpan.setTag("camel.uri", "direct://foo");
        customSpan.setTag("span.kind", "client");
        customSpan.finish();
    }
}
