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
package org.apache.camel.component.sjms.producer;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.TextMessage;

import com.example.external.ExternalText;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A body that is text but not a String (a StringBuilder, or a Groovy GString that a script returns) is sent as a
 * TextMessage. It was sent as an ObjectMessage, which is disabled by default.
 */
public class CharSequenceBodyTest extends JmsTestSupport {

    private static final String TEST_DESTINATION_NAME = "test.foo.CharSequenceBodyTest";
    private static final String TEST_OTHER_DESTINATION_NAME = "test.other.CharSequenceBodyTest";
    private static final String TEST_MAP_DESTINATION_NAME = "test.map.CharSequenceBodyTest";
    private static final String TEST_REPLY_DESTINATION_NAME = "test.reply.CharSequenceBodyTest";

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testStringBuilderIsSentAsText() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME);

        template.sendBody("sjms:queue:" + TEST_DESTINATION_NAME, new StringBuilder("Hello World"));

        Message message = mc.receive(5000);
        TextMessage text = assertInstanceOf(TextMessage.class, message);
        assertEquals("Hello World", text.getText());
        mc.close();
    }

    @Test
    public void testOtherTextIsSentAsText() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_OTHER_DESTINATION_NAME);

        // text that is not a String, as a Groovy GString
        template.sendBody("sjms:queue:" + TEST_OTHER_DESTINATION_NAME, new ExternalText("Bye World"));

        Message message = mc.receive(5000);
        TextMessage text = assertInstanceOf(TextMessage.class, message);
        assertEquals("Bye World", text.getText());
        mc.close();
    }

    @Test
    public void testTextInAMapIsSentAsString() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_MAP_DESTINATION_NAME);

        // a Map with text that is not a String, as a Groovy script that returns [status: "not enough ${sku}"]
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", new ExternalText("not enough CAMEL-CAP"));
        body.put("note", new StringBuilder("back order"));
        body.put("count", 3);
        template.sendBody("sjms:queue:" + TEST_MAP_DESTINATION_NAME, body);

        Message message = mc.receive(5000);
        MapMessage map = assertInstanceOf(MapMessage.class, message);
        assertEquals("not enough CAMEL-CAP", map.getObject("status"));
        assertEquals("back order", map.getObject("note"));
        assertEquals(3, map.getObject("count"));
        mc.close();
    }

    @Test
    public void testTextReply() {
        Object reply = template.requestBody("sjms:queue:" + TEST_REPLY_DESTINATION_NAME + "?requestTimeout=5000", "CAMEL-CAP");
        assertEquals("not enough CAMEL-CAP", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a reply that is text but not a String, as a route that sets the body with a Groovy GString
                from("sjms:queue:" + TEST_REPLY_DESTINATION_NAME)
                        .process(e -> e.getMessage().setBody(new ExternalText("not enough " + e.getMessage().getBody())));
            }
        };
    }
}
