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

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMessage;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 *
 */
public class HttpJettyProducerTwoParametersWithSameKeyTest extends BaseJettyTest {

    @Test
    public void testTwoParametersWithSameKey() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        // give Jetty time to startup properly
        Thread.sleep(1000);

        Exchange a = template.request("jetty:http://localhost:{{port}}/myapp?from=me&to=foo&to=bar", null);
        assertNotNull(a);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/myapp").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String from = exchange.getIn().getHeader("from", String.class);
                        assertEquals("me", from);

                        HttpServletRequest req = exchange.getIn(HttpMessage.class).getRequest();

                        // TODO: Http/Jetty should support headers with multiple values
                        String[] values = req.getParameterValues("to");
                        assertNotNull(values);
                        assertEquals(2, values.length);
                        assertEquals("foo", values[0]);
                        assertEquals("bar", values[1]);

                        exchange.getOut().setBody("OK");
                    }
                });
            }
        };
    }

}
