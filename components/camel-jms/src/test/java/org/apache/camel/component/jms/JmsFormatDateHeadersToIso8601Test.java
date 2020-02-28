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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsFormatDateHeadersToIso8601Test extends CamelTestSupport {

    private static final Date DATE = Date.from(Instant.ofEpochMilli(1519672338000L));

    @Test
    public void testComponentFormatDateHeaderToIso8601() throws Exception {
        String outDate = template.requestBodyAndHeader("direct:start-isoformat", "body", "date", DATE, String.class);
        assertEquals(outDate, "2018-02-26T19:12:18Z");
    }

    @Test
    public void testBindingFormatDateHeaderToIso8601() throws Exception {
        String outDate = template.requestBodyAndHeader("direct:start-nonisoformat", "body", "date", DATE, String.class);
        assertNotEquals(outDate, "2018-02-26T19:12:18Z");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.getConfiguration().setFormatDateHeadersToIso8601(true);
        camelContext.addComponent("activemq", jms);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start-isoformat").to("activemq:queue:foo");
                from("direct:start-nonisoformat").to("activemq:queue:foo?formatDateHeadersToIso8601=false");
                from("activemq:queue:foo").setBody(simple("${in.header.date}"));
            }
        };
    }
}
