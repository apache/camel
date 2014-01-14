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
package org.apache.camel.component.jetty.jettyproducer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 * @version 
 */
public class HttpJettyProducerRecipientListCustomThreadPoolTest extends BaseJettyTest {

    @Test
    public void testRecipientList() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        // give Jetty time to startup properly
        Thread.sleep(1000);

        Exchange a = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("slip", "jetty://http://localhost:" + getPort() + "/myapp?foo=123&bar=cheese&httpClientMinThreads=4&httpClientMaxThreads=8");
            }
        });
        assertNotNull(a);

        assertEquals("Bye cheese", a.getOut().getBody(String.class));
        assertEquals(246, a.getOut().getHeader("foo", Integer.class).intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").recipientList(header("slip"));

                from("jetty://http://localhost:{{port}}/myapp").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        int foo = exchange.getIn().getHeader("foo", Integer.class);
                        String bar = exchange.getIn().getHeader("bar", String.class);

                        exchange.getOut().setHeader("foo", foo * 2);
                        exchange.getOut().setBody("Bye " + bar);
                    }
                });
            }
        };
    }
}