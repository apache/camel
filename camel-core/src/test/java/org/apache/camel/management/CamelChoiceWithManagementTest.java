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
package org.apache.camel.management;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

/**
 * @version 
 */
public class CamelChoiceWithManagementTest extends ContextTestSupport {
    private MockEndpoint a;
    private MockEndpoint b;
    private MockEndpoint c;
    private MockEndpoint d;
    private MockEndpoint e;

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected void setUp() throws Exception {
        super.setUp();
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
        c = getMockEndpoint("mock:c");
        d = getMockEndpoint("mock:d");
        e = getMockEndpoint("mock:e");
    }

    public void testFirstChoiceRoute() throws Exception {
        final String body = "<one/>";
        a.expectedBodiesReceived(body);
        a.expectedHeaderReceived("CBR1", "Yes");
        c.expectedBodiesReceived(body);
        c.expectedHeaderReceived("CBR1", "Yes");
        c.expectedHeaderReceived("Validation", "Yes");
        expectsMessageCount(0, b, d, e);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
                exchange.getIn().setHeader("CBR1", "Yes");
            }
        });

        assertMockEndpointsSatisfied();
    }

    public void testOtherwise() throws Exception {
        final String body = "<None/>";
        e.expectedBodiesReceived(body);

        expectsMessageCount(0, a, b, c, d);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .choice()
                        .when(header("CBR1").isEqualTo("Yes")).to("mock:a").setHeader("Validation", constant("Yes"))
                        .when(header("CBR1").isEqualTo("No")).to("mock:b").end()
                    .choice().when(header("Validation").isEqualTo("Yes")).to("mock:c")
                        .when(header("Validation").isEqualTo("No")).to("mock:d").otherwise().to("mock:e").end();
            }
        };
    }

}
