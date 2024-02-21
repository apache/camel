/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws.xray.bean;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.xray.AWSXRay;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Handler;
import org.apache.camel.Headers;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.aws.xray.XRayTrace;
import org.apache.camel.component.aws.xray.XRayTracer;

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
