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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.jms.MapMessage;
import jakarta.jms.TextMessage;

import com.example.external.ExternalText;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A body that is text but not a String (a StringBuilder, or a Groovy GString that a script returns) is sent as a
 * TextMessage. It was sent as an ObjectMessage, which is disabled by default.
 */
public class JmsCharSequenceBodyTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Test
    public void testStringBuilderIsSentAsText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).body().isInstanceOf(String.class);

        template.sendBody("jms:queue:JmsCharSequenceBodyTest", new StringBuilder("Hello World"));

        MockEndpoint.assertIsSatisfied(context);
        JmsMessage in = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class);
        assertInstanceOf(TextMessage.class, in.getJmsMessage());
    }

    @Test
    public void testTextInAMapIsSentAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:map");
        mock.expectedMessageCount(1);

        // a Map with text that is not a String, as a Groovy script that returns [status: "not enough ${sku}"]
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", new ExternalText("not enough CAMEL-CAP"));
        body.put("note", new StringBuilder("back order"));
        body.put("count", 3);
        template.sendBody("jms:queue:JmsCharSequenceBodyTest.map", body);

        MockEndpoint.assertIsSatisfied(context);
        JmsMessage in = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class);
        assertInstanceOf(MapMessage.class, in.getJmsMessage());
        Map<?, ?> received = in.getBody(Map.class);
        assertEquals("not enough CAMEL-CAP", received.get("status"));
        assertEquals("back order", received.get("note"));
        assertEquals(3, received.get("count"));
    }

    @Test
    public void testTextReply() {
        Object reply = template.requestBody("jms:queue:JmsCharSequenceBodyTest.reply?requestTimeout=5000", "CAMEL-CAP");
        assertEquals("not enough CAMEL-CAP", reply);
    }

    @Test
    public void testTextReplyWithObjectMessageEnabled() {
        // the reply is text, so it is not a serialized Java object that the deserialization filter blocks
        Object reply = template.requestBody(
                "jms:queue:JmsCharSequenceBodyTest.enabled?objectMessageEnabled=true&requestTimeout=5000", "CAMEL-CAP");
        assertEquals("not enough CAMEL-CAP", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsCharSequenceBodyTest").to("mock:result");

                from("jms:queue:JmsCharSequenceBodyTest.map").to("mock:map");

                // a reply that is text but not a String, as a route that sets the body with a Groovy GString
                from("jms:queue:JmsCharSequenceBodyTest.reply")
                        .process(e -> e.getMessage().setBody(new ExternalText("not enough " + e.getMessage().getBody())));

                from("jms:queue:JmsCharSequenceBodyTest.enabled?objectMessageEnabled=true")
                        .process(e -> e.getMessage().setBody(new ExternalText("not enough " + e.getMessage().getBody())));
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }
}
