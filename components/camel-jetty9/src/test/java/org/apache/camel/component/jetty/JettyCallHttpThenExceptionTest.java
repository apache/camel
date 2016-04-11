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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class JettyCallHttpThenExceptionTest extends BaseJettyTest {

    @Test
    public void testJettyCallHttpThenException() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");

        Exchange reply = template.request("http://localhost:{{port}}/myserver?throwExceptionOnFailure=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("World");
            }
        });

        assertMockEndpointsSatisfied();

        assertNotNull(reply);
        assertTrue(reply.getOut().getBody(String.class).startsWith("java.lang.IllegalArgumentException: I cannot do this"));
        assertEquals(500, reply.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Server Error", reply.getOut().getHeader(Exchange.HTTP_RESPONSE_TEXT));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/myserver")
                    .to("log:A")
                    // remove http headers before and after invoking http service
                    .removeHeaders("CamelHttp*")
                    .to("http://localhost:{{port}}/other")
                    .removeHeaders("CamelHttp*")
                    .to("mock:bar")
                    // now just force an exception immediately
                    .throwException(new IllegalArgumentException("I cannot do this"));

                from("jetty://http://localhost:{{port}}/other")
                    .convertBodyTo(String.class)
                    .to("log:C")
                    .to("mock:foo")
                    .transform().simple("Bye ${body}");
            }
        };
    }
    
}