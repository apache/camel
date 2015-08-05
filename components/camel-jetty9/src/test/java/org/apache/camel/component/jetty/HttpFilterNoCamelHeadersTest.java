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
public class HttpFilterNoCamelHeadersTest extends BaseJettyTest {

    @Test
    public void testFilterCamelHeaders() throws Exception {
        // the Camel file name header should be preserved during routing
        // but should not be sent over HTTP
        // and jetty should not send back CamelDummy header

        getMockEndpoint("mock:input").expectedMessageCount(1);
        getMockEndpoint("mock:input").message(0).header("bar").isEqualTo(123);
        getMockEndpoint("mock:input").message(0).header(Exchange.FILE_NAME).isNull();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).header("bar").isEqualTo(123);
        getMockEndpoint("mock:result").message(0).header(Exchange.FILE_NAME).isEqualTo("test.txt");
        getMockEndpoint("mock:result").message(0).header("CamelDummy").isNull();

        Exchange out = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("World");
                exchange.getIn().setHeader("bar", 123);
            }
        });

        assertNotNull(out);
        assertEquals("Bye World", out.getOut().getBody(String.class));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setHeader(Exchange.FILE_NAME, constant("test.txt"))
                    .to("jetty:http://localhost:{{port}}/test/filter")
                    .to("mock:result");

                from("jetty:http://localhost:{{port}}/test/filter")
                    .to("mock:input")
                    .setHeader("CamelDummy", constant("dummy"))
                    .transform(simple("Bye ${body}"));
            }
        };
    }

}
