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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 *
 */
public class TryCatchCaughtExceptionFinallyTest extends ContextTestSupport {

    @Test
    public void testTryCatchCaughtExceptionFinally() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", this);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry().to("mock:a").to("bean:myBean?method=doSomething").doCatch(Exception.class).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("bean://myBean?method=doSomething", exchange.getProperty(Exchange.FAILURE_ENDPOINT));
                        assertEquals("Forced", exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
                    }
                }).doFinally().process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("bean://myBean?method=doSomething", exchange.getProperty(Exchange.FAILURE_ENDPOINT));
                        assertEquals("Forced", exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
                    }
                }).end().to("mock:result");
            }
        };
    }

    public void doSomething(String body) throws Exception {
        throw new IllegalArgumentException("Forced");
    }
}
