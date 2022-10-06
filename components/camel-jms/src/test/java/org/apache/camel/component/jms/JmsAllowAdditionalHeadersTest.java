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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;

public class JmsAllowAdditionalHeadersTest extends AbstractJMSTest {

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

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        JmsComponent component = super.setupComponent(camelContext, service, componentName);

        // allow any of those special IBM headers (notice we use * as wildcard)
        component.getConfiguration().setAllowAdditionalHeaders("JMS_IBM_MQMD*");

        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("jms:queue:JmsAllowAdditionalHeadersTest");

                from("jms:queue:JmsAllowAdditionalHeadersTest").to("mock:bar");
            }
        };
    }
}
