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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for in optional out
 */
public class RouteMEPOptionalOutTest extends ContextTestSupport {

    @Test
    public void testHasOut() {
        Object out = template.requestBody("direct:start", "Hi");
        assertEquals("Bye World", out);
    }

    @Test
    public void testHasNotOutForInOptionalOut() {
        // OUT is optional in the route but we should not get a response
        Object out = template.sendBody("direct:noout", ExchangePattern.InOptionalOut, "Hi");
        assertEquals(null, out);
    }

    @Test
    public void testHasNotOutForInOut() {
        // OUT is optional in the route but we should still not get a response
        Object out = template.sendBody("direct:noout", ExchangePattern.InOut, "Hi");
        assertEquals(null, out);
    }

    @Test
    public void testHasNotOutForInOnly() {
        Object out = template.sendBody("direct:noout", ExchangePattern.InOnly, "Hi");
        assertEquals(null, out);
    }

    @Test
    public void testInOnly() {
        Object out = template.sendBody("direct:inonly", ExchangePattern.InOnly, "Hi");
        assertEquals(null, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setExchangePattern(ExchangePattern.InOptionalOut).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Bye World");
                    }
                });

                from("direct:noout").setExchangePattern(ExchangePattern.InOptionalOut).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // we do not set any OUT only IN
                        exchange.getIn().setBody("Hello World");
                    }
                });

                from("direct:inonly").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody("Hello World");
                    }
                });
            }
        };
    }
}
