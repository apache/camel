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
package org.apache.camel.component.netty4.http;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NettyHttpFilterCamelHeadersTest extends BaseNettyTest {

    @Test
    public void testFilterCamelHeaders() throws Exception {
        Exchange out = template.request("netty4-http:http://localhost:{{port}}/test/filter", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Claus");
                exchange.getIn().setHeader("bar", 123);
            }
        });

        assertNotNull(out);
        assertEquals("Hi Claus", out.getOut().getBody(String.class));

        // there should be no internal Camel headers
        // except for the response code and response text
        Map<String, Object> headers = out.getOut().getHeaders();
        for (String key : headers.keySet()) {
            if (!key.equalsIgnoreCase(Exchange.HTTP_RESPONSE_CODE) && !key.equalsIgnoreCase(Exchange.HTTP_RESPONSE_TEXT)) {
                assertTrue("Should not contain any Camel internal headers", !key.toLowerCase().startsWith("camel"));
            }
        }
        assertEquals(200, headers.get(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://localhost:{{port}}/test/filter").bean("foo");
            }
        };
    }

    public static class MyFooBean {

        public String hello(String name) {
            return "Hi " + name;
        }
    }

}
