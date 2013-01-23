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
package org.apache.camel.processor.routingslip;

import javax.naming.Context;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;

public class RoutingSlipDataModificationTest extends ContextTestSupport {
    protected static final String ANSWER = "answer";
    protected static final String ROUTING_SLIP_HEADER = "routingSlipHeader";
    protected MyBean myBean = new MyBean();

    public void testModificationOfDataAlongRoute()
        throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");

        x.expectedBodiesReceived(ANSWER);
        y.expectedBodiesReceived(ANSWER + ANSWER);

        sendBody();

        assertMockEndpointsSatisfied();
    }

    protected void sendBody() {
        template.sendBodyAndHeader("direct:a", ANSWER, ROUTING_SLIP_HEADER,
                "mock:x , bean:myBean?method=modifyData");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Object lookedUpBean = context.getRegistry().lookupByName("myBean");
        assertSame("Lookup of 'myBean' should return same object!", myBean, lookedUpBean);
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);
        return answer;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                // START SNIPPET: example
                from("direct:a").routingSlip(header(ROUTING_SLIP_HEADER)).to("mock:y");
                // END SNIPPET: example
            }
        };
    }

    public static class MyBean {
        public MyBean() {
        }

        public String modifyData(@Body String body) {
            return body + body;
        }
    }
}
