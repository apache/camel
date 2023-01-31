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

import java.time.Instant;
import java.util.Date;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisVMService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JmsFormatDateHeadersToIso8601Test extends CamelTestSupport {

    private static final Date DATE = Date.from(Instant.ofEpochMilli(1519672338000L));

    @RegisterExtension
    public ArtemisService service = new ArtemisVMService();

    @Test
    public void testComponentFormatDateHeaderToIso8601() {
        String outDate = template.requestBodyAndHeader("direct:start-isoformat", "body", "date", DATE, String.class);
        assertEquals("2018-02-26T19:12:18Z", outDate);
    }

    @Test
    public void testBindingFormatDateHeaderToIso8601() {
        String outDate = template.requestBodyAndHeader("direct:start-nonisoformat", "body", "date", DATE, String.class);
        assertNotEquals("2018-02-26T19:12:18Z", outDate);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // Note: this one does something strange that requires a fresh new broker
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service, 0);

        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.getConfiguration().setFormatDateHeadersToIso8601(true);
        camelContext.addComponent("activemq", jms);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start-isoformat").to("activemq:queue:JmsFormatDateHeadersToIso8601Test");
                from("direct:start-nonisoformat")
                        .to("activemq:queue:JmsFormatDateHeadersToIso8601Test?formatDateHeadersToIso8601=false");
                from("activemq:queue:JmsFormatDateHeadersToIso8601Test").setBody(simple("${in.header.date}"));
            }
        };
    }
}
