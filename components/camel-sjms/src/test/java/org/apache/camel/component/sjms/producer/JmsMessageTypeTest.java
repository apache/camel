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

import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsMessageTypeTest extends JmsTestSupport {

    private static final String TEST_DESTINATION_NAME = "test.foo.JmsMessageTypeTest";

    public JmsMessageTypeTest() {
    }

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testJmsMessageType() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Hello World!";

        template.sendBody("direct:start", expectedBody);
        Message message = mc.receive(5000);
        assertNotNull(message);
        assertTrue(message instanceof BytesMessage);

        BytesMessage tm = (BytesMessage) message;
        long len = tm.getBodyLength();
        assertEquals(12, len);

        byte[] arr = new byte[12];
        tm.readBytes(arr);
        assertTrue(ObjectHelper.equalByteArray(expectedBody.getBytes(), arr));

        mc.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("sjms:queue:" + TEST_DESTINATION_NAME + "?jmsMessageType=Bytes");
            }
        };
    }
}
