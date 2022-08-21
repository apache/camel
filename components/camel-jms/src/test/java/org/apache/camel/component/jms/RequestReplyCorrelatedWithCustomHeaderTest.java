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

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestReplyCorrelatedWithCustomHeaderTest extends AbstractJMSTest {

    public static void processRequest(
            @Body final String body,
            @Header("CustomCorrelation") final String customCorrelation,
            @Header("JMSCorrelationId") final String jmsCorrelationId, final Exchange exchange) {
        assertNotNull(customCorrelation);
        assertNull(jmsCorrelationId);
        exchange.getIn().setBody("Hi, " + body + ", " + customCorrelation);
    }

    @Test
    public void shouldCorrelateRepliesWithCustomCorrelationProperty() {
        final String reply = template.requestBody("activemq:queue:request",
                "Bobby", String.class);

        assertTrue(reply.matches("Hi, Bobby, Camel-.*"));
    }

    @Test
    public void shouldCorrelateRepliesWithCustomCorrelationPropertyAndValue() {
        final String reply = template.requestBodyAndHeader(
                "activemq:queue:request", "Bobby", "CustomCorrelation",
                "custom-id", String.class);

        assertEquals("Hi, Bobby, custom-id", reply);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);
        component.getConfiguration().setCorrelationProperty("CustomCorrelation");
        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:request").bean(RequestReplyCorrelatedWithCustomHeaderTest.class, "processRequest");
            }
        };
    }
}
