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

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Based on user forum.
 */
public class JmsHttpPostIssueTest extends CamelTestSupport {

    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private int port;

    @Test
    void testJmsInOnlyHttpPostIssue() {
        NotifyBuilder notify = new NotifyBuilder(context).whenCompleted(1).from("jms*").create();

        template.sendBody("jms:queue:in", "Hello World");

        assertTrue(notify.matchesWaitTime(), "Should complete the JMS route");
    }

    @Test
    void testJmsInOutHttpPostIssue() {
        String out = template.requestBody("jms:queue:in", "Hello World", String.class);
        assertEquals("OK", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jms:queue:in")
                        .setBody().simple("name=${body}")
                        .setHeader(CONTENT_TYPE).constant("application/x-www-form-urlencoded")
                        .setHeader(HTTP_METHOD).constant("POST")
                        .to("http://localhost:" + port + "/myservice");

                from("jetty:http://0.0.0.0:" + port + "/myservice")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            assertEquals("name=Hello World", body);

                            exchange.getMessage().setBody("OK");
                            exchange.getMessage().setHeader(CONTENT_TYPE, "text/plain");
                            exchange.getMessage().setHeader(HTTP_RESPONSE_CODE, 200);
                        });
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);

        registry.bind("jms", amq);
    }

}
