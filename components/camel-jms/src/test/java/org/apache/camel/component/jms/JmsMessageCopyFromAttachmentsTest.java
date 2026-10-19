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

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.trait.message.MessageTrait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for CAMEL-25779: JmsMessage.copyFrom() must preserve the ATTACHMENTS trait.
 * <p>
 * CAMEL-23193 added the copy; CAMEL-24365 incorrectly removed it treating it as redundant with
 * AbstractExchange.newOutMessage(). The two code paths are different: newOutMessage() covers the exchange.getOut()
 * case, while copyFrom() covers exchange copies (e.g. Multicast EIP). This test pins the correct behaviour so the
 * regression cannot go unnoticed again.
 */
class JmsMessageCopyFromAttachmentsTest {

    @Test
    void copyFromPreservesAttachments() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultExchange exchange = new DefaultExchange(context);

            // source: a JmsMessage with one attachment
            JmsMessage source = new JmsMessage(exchange, null, null, null);
            source.setBody("Hello");
            Map<String, Object> attachments = new LinkedHashMap<>();
            attachments.put("text.txt", "Hello!");
            source.setPayloadForTrait(MessageTrait.ATTACHMENTS, attachments);

            // target: a fresh JmsMessage
            JmsMessage target = new JmsMessage(exchange, null, null, null);
            target.copyFrom(source);

            // the attachment must be present on the copy
            @SuppressWarnings("unchecked")
            Map<String, Object> copied = (Map<String, Object>) target.getPayloadForTrait(MessageTrait.ATTACHMENTS);
            assertNotNull(copied, "copyFrom() must copy the ATTACHMENTS trait (regression CAMEL-25779)");
            assertEquals(1, copied.size());
            assertEquals("Hello!", copied.get("text.txt"));

            // the copy must be a distinct map (defensive copy, not an alias)
            assertNotSame(attachments, copied, "copied attachment map must be a new instance");
        }
    }

    @Test
    void copyFromWithNoAttachmentsLeavesTraitNull() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultExchange exchange = new DefaultExchange(context);

            // source with no attachments
            JmsMessage source = new JmsMessage(exchange, null, null, null);
            source.setBody("Hello");

            JmsMessage target = new JmsMessage(exchange, null, null, null);
            target.copyFrom(source);

            assertNull(target.getPayloadForTrait(MessageTrait.ATTACHMENTS),
                    "copyFrom() from a message with no attachments must not set the attachment trait");
        }
    }
}
