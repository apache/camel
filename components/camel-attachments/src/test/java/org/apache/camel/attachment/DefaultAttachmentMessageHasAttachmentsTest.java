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
package org.apache.camel.attachment;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.trait.message.MessageTrait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for CAMEL-24398: hasAttachments() must reflect actual attachment content, not merely trait
 * registration after read-only attachment inspection.
 */
class DefaultAttachmentMessageHasAttachmentsTest extends CamelTestSupport {

    @Test
    void hasAttachmentsIsFalseForPlainMessage() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);

        assertFalse(message.hasAttachments());
    }

    @Test
    void hasAttachmentsIsFalseAfterReadOnlyGetAttachments() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);

        assertTrue(message.getAttachments().isEmpty());
        assertFalse(message.hasAttachments());
    }

    @Test
    void hasAttachmentsIsFalseAfterReadOnlyGetAttachmentNames() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);

        assertTrue(message.getAttachmentNames().isEmpty());
        assertFalse(message.hasAttachments());
    }

    @Test
    void hasAttachmentsIsFalseAfterReadOnlyGetAttachmentObjects() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);

        assertTrue(message.getAttachmentObjects().isEmpty());
        assertFalse(message.hasAttachments());
    }

    @Test
    void hasAttachmentsIsFalseAfterReadOnlyGetAttachment() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);

        assertNull(message.getAttachment("missing"));
        assertFalse(message.hasAttachments());
    }

    @Test
    void hasAttachmentsIsFalseAfterAttachmentMessageConversionWithoutAttachments() {
        Exchange e = createExchangeWithBody("<order/>");
        AttachmentMessage message = AttachmentConverter.toAttachmentMessage(e.getMessage());

        assertTrue(message.getAttachments().isEmpty());
        assertFalse(message.hasAttachments());
        assertTrue(message.hasTrait(MessageTrait.ATTACHMENTS));
    }

    @Test
    void hasAttachmentsIsTrueAfterAddingAttachment() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);
        message.addAttachment("payload.xml", new DataHandler("<order/>", "application/xml"));

        assertTrue(message.hasAttachments());
        assertTrue(message.getAttachmentNames().contains("payload.xml"));
    }

    @Test
    void hasAttachmentsIsFalseAfterRemovingLastAttachment() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);
        message.addAttachment("payload.xml", new DataHandler("<order/>", "application/xml"));
        message.removeAttachment("payload.xml");

        assertFalse(message.hasAttachments());
        assertTrue(message.getAttachmentNames().isEmpty());
    }

    @Test
    void hasAttachmentsIsFalseAfterClearAttachments() {
        Exchange e = createExchangeWithBody("");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);
        message.addAttachment("payload.xml", new DataHandler("<order/>", "application/xml"));

        message.clearAttachments();

        assertFalse(message.hasAttachments());
        assertFalse(message.hasTrait(MessageTrait.ATTACHMENTS));
    }

    @Test
    void simulatesHttpBindingPopulateAttachmentsFlow() {
        Exchange e = createExchangeWithBody("<?xml version=\"1.0\"?><order/>");
        AttachmentMessage message = e.getMessage(AttachmentMessage.class);
        message.getAttachments();

        assertFalse(message.hasAttachments(),
                "plain XML must not be treated as multipart when no attachments were added");
    }
}
