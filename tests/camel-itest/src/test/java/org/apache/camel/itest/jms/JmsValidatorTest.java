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
package org.apache.camel.itest.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsValidatorTest extends CamelTestSupport {

    @Test
    public void testJmsValidator() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(1);
        getMockEndpoint("mock:invalid").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<p>Hello world!</p>";
        template.sendBody("jms:queue:inbox", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsValidatorInvalid() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(0);
        getMockEndpoint("mock:invalid").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<foo>Kaboom</foo>";
        template.sendBody("jms:queue:inbox", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        registry.bind("jms", amq);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:inbox")
                    .convertBodyTo(String.class)
                    .doTry()
                        .to("validator:file:src/test/resources/myschema.xsd")
                        .to("jms:queue:valid")
                    .doCatch(ValidationException.class)
                        .to("jms:queue:invalid")
                    .doFinally()
                        .to("jms:queue:finally")
                    .end();

                from("jms:queue:valid").to("mock:valid");
                from("jms:queue:invalid").to("mock:invalid");
                from("jms:queue:finally").to("mock:finally");
            }
        };
    }
}
