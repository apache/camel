package org.apache.camel.component.aws.xray.bean;

import com.amazonaws.xray.AWSXRay;
import org.apache.camel.*;
import org.apache.camel.component.aws.xray.XRayTrace;
import org.apache.camel.component.aws.xray.XRayTracer;

import java.util.HashMap;
import java.util.Map;

@XRayTrace
public class SomeBean {

    @Handler
    public void doSomething(@Headers Map<String, Object> headers, CamelContext context) {

        ProducerTemplate template = context.createProducerTemplate();
        String body = "New exchange test";

        Endpoint testEndpoint = template.getCamelContext().getEndpoint("seda:test");
        Exchange exchange = testEndpoint.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(body);

        Map<String, Object> newHeaders = new HashMap<>();
        // as we create a completely new exchange, this exchange has no trace ID yet specified and would result in a new
        // trace ID being generated which would present a flawed view if viewed in the AWS XRay console
        newHeaders.put(XRayTracer.XRAY_TRACE_ID, headers.get(XRayTracer.XRAY_TRACE_ID));
        // store the current AWS XRay trace entity (= segment or subsegment) into the headers
        newHeaders.put(XRayTracer.XRAY_TRACE_ENTITY, AWSXRay.getGlobalRecorder().getTraceEntity());
        exchange.getIn().setHeaders(newHeaders);
        template.asyncSend(testEndpoint, exchange);
    }
}
