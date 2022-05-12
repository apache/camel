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
package org.apache.camel.component.jetty;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JettyStreamCacheIssueTest extends BaseJettyTest {
    private String input;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // ensure we overflow and spool to disk
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        context.getStreamCachingStrategy().setSpoolThreshold(5000);
        context.setStreamCaching(true);
        return context;
    }

    @Test
    public void testStreamCache() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("0123456789");
        }
        input = sb.toString();

        String out = template.requestBody("direct:input", input, String.class);
        assertEquals(input, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input").to("http://localhost:" + getPort() + "/input");

                from("jetty:http://localhost:" + getPort() + "/input").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) {
                        // Get message returns the in message if an out one is not present, which is the expectation here
                        assertEquals(input, exchange.getMessage().getBody(String.class));
                    }
                });
            }
        };
    }

}
