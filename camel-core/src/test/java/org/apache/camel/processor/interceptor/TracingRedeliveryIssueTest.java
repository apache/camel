/**
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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class TracingRedeliveryIssueTest extends Assert {

    @Test
    public void testTracing() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(2000L));

                from("direct:start").to("mock:result");
            }
        });

        // Enable Tracer.
        context.setTracing(true);
        Tracer tracer = new Tracer();
        tracer.setDestinationUri("mock:traced");
        context.setDefaultTracer(tracer);
        context.start();

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.setExpectedMessageCount(1);
        MockEndpoint traced = context.getEndpoint("mock:traced", MockEndpoint.class);
        traced.setExpectedMessageCount(1);

        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("direct:start", "foo");

        MockEndpoint.assertIsSatisfied(result, traced);

        context.stop();
    }

}
