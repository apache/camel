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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class TracerTest extends ContextTestSupport {

    private Tracer tracer;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("traceFormatter", new DefaultTraceFormatter());
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        tracer = Tracer.createTracer(context);
        tracer.setEnabled(true);
        tracer.setTraceInterceptors(true);
        tracer.setTraceFilter(body().contains("Camel"));
        tracer.setTraceExceptions(true);
        tracer.setLogStackTrace(true);
        tracer.setUseJpa(false);
        tracer.setDestination(context.getEndpoint("mock:traced"));

        context.addInterceptStrategy(tracer);
        tracer.start();

        return context;
    }

    @Override
    protected void tearDown() throws Exception {
        tracer.stop();
        super.tearDown();
    }

    public void testTracer() throws Exception {
        MockEndpoint tracer = getMockEndpoint("mock:traced");
        tracer.expectedMessageCount(1);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        DefaultTraceEventMessage em = tracer.getReceivedExchanges().get(0).getIn().getBody(DefaultTraceEventMessage.class);
        assertEquals("Hello Camel", em.getBody());

        assertEquals("String", em.getBodyType());
        assertEquals(null, em.getCausedByException());
        assertNotNull(em.getExchangeId());
        assertNotNull(em.getShortExchangeId());
        assertNotNull(em.getExchangePattern());
        assertEquals("direct://start", em.getFromEndpointUri());
        // there is always a breadcrumb header
        assertNotNull(em.getHeaders());
        assertNotNull(em.getProperties());
        assertNull(em.getOutBody());
        assertNull(em.getOutBodyType());
        assertNull(em.getOutHeaders());
        assertNull(em.getPreviousNode());
        assertNotNull(em.getToNode());
        assertNotNull(em.getTimestamp());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}
