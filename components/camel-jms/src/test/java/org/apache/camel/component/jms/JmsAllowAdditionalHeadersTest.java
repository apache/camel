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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsAllowAdditionalHeadersTest extends CamelTestSupport {

    @Test
    public void testAllowAdditionalHeaders() throws Exception {
        // byte[] data = "Camel Rocks".getBytes();
        Object data = "Camel Rocks";

        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedHeaderReceived("foo", "bar");
        // ActiveMQ will not accept byte[] value
        // getMockEndpoint("mock:bar").expectedHeaderReceived("JMS_IBM_MQMD_USER", data);

        fluentTemplate.withBody("Hello World").withHeader("foo", "bar").withHeader("JMS_IBM_MQMD_USER", data)
            .to("direct:start").send();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();

        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        // allow any of those special IBM headers (notice we use * as wildcard)
        jms.getConfiguration().setAllowAdditionalHeaders("JMS_IBM_MQMD*");

        camelContext.addComponent("jms", jms);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("jms:queue:bar");

                from("jms:queue:bar").to("mock:bar");
            }
        };
    }
}
