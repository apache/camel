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
package org.apache.camel.support;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.DataType;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.trait.message.RedeliveryTraitPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Most messages never get a trait, so the traits of a message are only stored once one is set. A message without traits
 * must behave the same as before for every trait operation.
 */
class MessageSupportTraitsTest {

    @Test
    void messageWithoutTraits() {
        DefaultMessage message = new DefaultMessage((Exchange) null);

        for (MessageTrait trait : MessageTrait.values()) {
            assertFalse(message.hasTrait(trait), trait.name());
            assertNull(message.getPayloadForTrait(trait), trait.name());
        }
        assertNull(message.getDataType());
        // removing a trait that is not set is a no-op
        message.removeTrait(MessageTrait.REDELIVERY);
        assertFalse(message.hasTrait(MessageTrait.REDELIVERY));
        message.reset();
        assertFalse(message.hasTrait(MessageTrait.REDELIVERY));
    }

    @Test
    void setGetAndRemoveTrait() {
        DefaultMessage message = new DefaultMessage((Exchange) null);

        message.setPayloadForTrait(MessageTrait.REDELIVERY, RedeliveryTraitPayload.IS_REDELIVERY);
        assertTrue(message.hasTrait(MessageTrait.REDELIVERY));
        assertSame(RedeliveryTraitPayload.IS_REDELIVERY, message.getPayloadForTrait(MessageTrait.REDELIVERY));
        assertFalse(message.hasTrait(MessageTrait.DATA_AWARE));

        // a trait set with a null payload is still a trait of the message
        message.setPayloadForTrait(MessageTrait.DATA_AWARE, null);
        assertTrue(message.hasTrait(MessageTrait.DATA_AWARE));
        assertNull(message.getPayloadForTrait(MessageTrait.DATA_AWARE));

        message.removeTrait(MessageTrait.REDELIVERY);
        assertFalse(message.hasTrait(MessageTrait.REDELIVERY));
        assertTrue(message.hasTrait(MessageTrait.DATA_AWARE));

        message.reset();
        assertFalse(message.hasTrait(MessageTrait.DATA_AWARE));
    }

    @Test
    void copyKeepsDataTypeAndAttachments() {
        DefaultMessage message = new DefaultMessage((Exchange) null);
        DataType dataType = new DataType("foo:bar");
        Map<String, Object> attachments = Map.of("a", "b");
        message.setBody("Hello", dataType);
        message.setPayloadForTrait(MessageTrait.ATTACHMENTS, attachments);

        Message copy = new DefaultMessage((Exchange) null);
        copy.copyFrom(message);

        assertSame(dataType, copy.getPayloadForTrait(MessageTrait.DATA_AWARE));
        assertEquals(attachments, copy.getPayloadForTrait(MessageTrait.ATTACHMENTS));
        // the attachments of the copy can be changed without changing the attachments of the original message
        assertNotSame(attachments, copy.getPayloadForTrait(MessageTrait.ATTACHMENTS));
    }

    @Test
    void copyOfMessageWithoutTraitsHasNoTraits() {
        Message copy = new DefaultMessage((Exchange) null);
        copy.copyFrom(new DefaultMessage((Exchange) null));

        for (MessageTrait trait : MessageTrait.values()) {
            assertFalse(copy.hasTrait(trait), trait.name());
        }
    }
}
