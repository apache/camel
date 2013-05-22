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
package org.apache.camel.component.disruptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.CastUtils;
import org.junit.Test;

/**
 * @version
 */
public class DirectRequestReplyMultipleConsumersInOutTest extends CamelTestSupport {
    @Test
    public void testInOut() throws Exception {
        List<String> expectedBodies = new ArrayList<String>(Arrays.asList("Bye World-1", "Bye World-2"));
        getMockEndpoint("mock:log").expectedBodiesReceived(expectedBodies);

        final Exchange out = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        assertEquals("Bye World", getOutBody(out));
        final List<Exchange> groupedExchange = CastUtils.cast(
                out.getProperty(Exchange.GROUPED_EXCHANGE, List.class));

        assertNotNull("Expected a grouped exchange property, found none", groupedExchange);
        for (Exchange exchange : groupedExchange) {
            assertTrue("Body of grouped exchange property '" + getOutBody(exchange) + "' did not match any expected body", expectedBodies.remove(getOutBody(exchange)));
        }

        log.info("Got reply " + out);

        assertMockEndpointsSatisfied();
    }

    private Object getOutBody(Exchange exchange) {
        return (exchange.hasOut() ? exchange.getOut() : exchange.getIn()).getBody();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // send the message as InOnly to DISRUPTOR as we want to continue routing
                // (as we don't want to do request/reply over DISRUPTOR)
                // In EIP patterns the WireTap pattern is what this would be
                from("direct:start").transform(constant("Bye World")).inOut("disruptor:log?multipleConsumers=true");

                from("disruptor:log?multipleConsumers=true").delay(100).transform(body().append("-1"))
                        .to("mock:log");
                from("disruptor:log?multipleConsumers=true").delay(1000).transform(body().append("-2"))
                        .to("mock:log");
            }
        };
    }
}
