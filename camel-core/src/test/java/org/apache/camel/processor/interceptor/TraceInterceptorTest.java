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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.easymock.classextension.EasyMock;

public class TraceInterceptorTest extends ContextTestSupport {
    private TraceFormatter formatter;
    private Tracer tracer;

    @Override
    protected void setUp() throws Exception {
        formatter = EasyMock.createMock(TraceFormatter.class);
        tracer = new Tracer();
        super.setUp();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                tracer.setFormatter(formatter);
                getContext().addInterceptStrategy(tracer);
                from("direct:a").to("mock:a");
            }
        };
    }

    public void testTracerInterceptor() throws Exception {
        EasyMock.reset(formatter);
        formatter.format(EasyMock.isA(TraceInterceptor.class), EasyMock.isA(Exchange.class));
        EasyMock.expectLastCall().andReturn("Test").atLeastOnce();
        EasyMock.replay(formatter);
        template.sendBody("direct:a", "<hello>world!</hello>");
        EasyMock.verify(formatter);
    }

    public void testTracerDisabledInterceptor() throws Exception {
        tracer.setEnabled(false);
        try {
            testTracerInterceptor();
            fail("The tracer should not work");
        } catch (Throwable ex) {
            assertTrue("ex should AssertionError", ex instanceof AssertionError);
        }
    }

}
