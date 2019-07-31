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
import org.junit.Assert;
import org.junit.Test;

public class JettyStreamCacheIssueTest extends BaseJettyTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // ensure we overflow and spool to disk
        context.getStreamCachingStrategy().setSpoolThreshold(5000);
        context.setStreamCaching(true);
        return context;
    }

    @Test
    public void testStreamCache() throws Exception {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 10000; i++) {
            sb.append("0123456789");
        }
        String input = sb.toString();

        String out = template.requestBody("direct:input", input, String.class);
        assertEquals(input, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input").to("http://localhost:" + getPort() + "/input");

                from("jetty:http://localhost:" + getPort() + "/input").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        Assert.assertFalse(exchange.hasOut());
                    }
                });
            }
        };
    }

}
