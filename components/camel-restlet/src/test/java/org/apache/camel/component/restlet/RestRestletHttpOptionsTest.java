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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletHttpOptionsTest extends RestletTestSupport {

    @Test
    public void testRestletServerOptions() throws Exception {
        Exchange exchange = template.request("http://localhost:" + portNum + "/users/v1/customers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals(204, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("GET, OPTIONS", exchange.getOut().getHeader("ALLOW"));
        assertEquals(null, exchange.getOut().getBody(String.class));

        exchange = fluentTemplate.to("http://localhost:" + portNum + "/users/v1/123").withHeader(Exchange.HTTP_METHOD, "OPTIONS").send();
        assertEquals(204, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OPTIONS, PUT", exchange.getOut().getHeader("ALLOW"));
        assertEquals(null, exchange.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                restConfiguration().component("restlet").host("localhost").port(portNum);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("v1/customers")
                        .to("mock:customers")
                    .put("v1/{id}")
                        .to("mock:id");
            }
        };
    }
}
