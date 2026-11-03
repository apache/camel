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
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageFormatException;
import jakarta.jms.ObjectMessage;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body that maps to no JMS message type must fail the exchange, and not be sent as an empty JMS message.
 */
public class UnmappableBodyTest extends JmsTestSupport {

    private static final String TEST_DESTINATION_NAME = "test.foo.UnmappableBodyTest";
    private static final String TEST_NULL_DESTINATION_NAME = "test.null.UnmappableBodyTest";

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testUnmappableBodyFails() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("sjms:queue:" + TEST_DESTINATION_NAME, new MyPojo("World")));
        MessageFormatException cause = assertInstanceOf(MessageFormatException.class, e.getCause());
        assertTrue(cause.getMessage().contains(MyPojo.class.getName()), cause.getMessage());
        assertTrue(cause.getMessage().contains("java.io.Serializable"), cause.getMessage());
        assertTrue(cause.getMessage().contains("jmsMessageType"), cause.getMessage());
        assertTrue(cause.getMessage().contains("marshal the body"), cause.getMessage());

        // nothing is sent
        assertNull(mc.receive(1000));
        mc.close();
    }

    @Test
    public void testNullBodySendsEmptyMessage() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_NULL_DESTINATION_NAME);

        template.sendBody("sjms:queue:" + TEST_NULL_DESTINATION_NAME, null);

        Message message = mc.receive(5000);
        assertNotNull(message);
        assertFalse(message instanceof TextMessage || message instanceof BytesMessage || message instanceof MapMessage
                || message instanceof ObjectMessage || message instanceof StreamMessage,
                "Expected a plain JMS Message, got: " + message);
        mc.close();
    }

    /**
     * A POJO that is not Serializable and has no type converter to a JMS body.
     */
    public static class MyPojo {
        private final String name;

        public MyPojo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
