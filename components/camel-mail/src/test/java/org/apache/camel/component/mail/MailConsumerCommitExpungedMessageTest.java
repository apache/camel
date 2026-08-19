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
package org.apache.camel.component.mail;

import java.lang.reflect.Field;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessageRemovedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that processCommit() wraps MessageRemovedException (message already expunged on the IMAP server) with a
 * non-null cause message so that the error log never shows "Caused by: [... - null]".
 */
public class MailConsumerCommitExpungedMessageTest {

    @Test
    public void testCommitWithExpungedMessageProducesNonNullCause() throws Exception {
        // --- infrastructure mocks ---
        JavaMailSender sender = mock(JavaMailSender.class);
        Processor processor = mock(Processor.class);
        CamelContext camelContext = mock(CamelContext.class);
        ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
        ExchangeFactory ef = mock(ExchangeFactory.class);
        Session session = Session.getInstance(Mailbox.getSessionProperties(Protocol.imap));

        when(sender.getSession()).thenReturn(session);
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);

        // --- endpoint/configuration ---
        MailEndpoint endpoint = new MailEndpoint();
        endpoint.setCamelContext(camelContext);
        MailConfiguration config = new MailConfiguration();
        config.configureProtocol(Protocol.imap.name());
        config.setPort(Mailbox.getPort(Protocol.imap));
        config.setFolderName("INBOX");
        config.setDelete(false);
        endpoint.setConfiguration(config);

        // --- message mock: setFlag throws MessageRemovedException (no message text — simulates the null cause) ---
        Message mail = mock(Message.class);
        doThrow(new MessageRemovedException()).when(mail).setFlag(any(Flags.Flag.class), any(boolean.class));

        // --- folder mock: open and isOpen so processCommit doesn't bail early ---
        Folder folder = mock(Folder.class);
        when(folder.isOpen()).thenReturn(true);

        // --- exchange mock ---
        Exchange exchange = mock(Exchange.class);
        org.apache.camel.Message camelMsg = mock(org.apache.camel.Message.class);
        when(exchange.getIn()).thenReturn(camelMsg);
        when(camelMsg.getHeader(MailConstants.MAIL_COPY_TO, config.getCopyTo(), String.class)).thenReturn(null);
        when(camelMsg.getHeader(MailConstants.MAIL_MOVE_TO, config.getMoveTo(), String.class)).thenReturn(null);
        when(camelMsg.getHeader(MailConstants.MAIL_DELETE, config.isDelete(), boolean.class)).thenReturn(false);
        // removeProperty returns null uid — fine because protocol is imap (not pop3)
        when(exchange.removeProperty(MailConsumer.MAIL_MESSAGE_UID)).thenReturn(null);

        // --- capture exception handler calls ---
        ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);

        // --- build consumer and inject mocks via reflection ---
        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);
        consumer.setExceptionHandler(exceptionHandler);

        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        folderField.set(consumer, folder);

        // --- exercise ---
        consumer.processCommit(mail, exchange);

        // --- verify exception handler was called with a MessagingException whose message is non-null ---
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Throwable> causeCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(exceptionHandler).handleException(msgCaptor.capture(), any(Exchange.class), causeCaptor.capture());

        Throwable caught = causeCaptor.getValue();
        assertInstanceOf(MessagingException.class, caught,
                "Expected MessagingException wrapping MessageRemovedException");
        assertNotNull(caught.getMessage(),
                "Cause message must not be null — was: " + caught.getMessage());
        assertEquals("Message already removed/expunged on server (no flag update possible)", caught.getMessage());

        assertInstanceOf(MessageRemovedException.class, caught.getCause(),
                "Root cause must be the original MessageRemovedException");
    }

    @Test
    public void testCommitWithOtherMessagingExceptionPassedThroughAsIs() throws Exception {
        // --- infrastructure mocks ---
        JavaMailSender sender = mock(JavaMailSender.class);
        Processor processor = mock(Processor.class);
        CamelContext camelContext = mock(CamelContext.class);
        ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
        ExchangeFactory ef = mock(ExchangeFactory.class);
        Session session = Session.getInstance(Mailbox.getSessionProperties(Protocol.imap));

        when(sender.getSession()).thenReturn(session);
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);

        MailEndpoint endpoint = new MailEndpoint();
        endpoint.setCamelContext(camelContext);
        MailConfiguration config = new MailConfiguration();
        config.configureProtocol(Protocol.imap.name());
        config.setPort(Mailbox.getPort(Protocol.imap));
        config.setFolderName("INBOX");
        config.setDelete(false);
        endpoint.setConfiguration(config);

        MessagingException originalException = new MessagingException("Some other server error");

        Message mail = mock(Message.class);
        doThrow(originalException).when(mail).setFlag(any(Flags.Flag.class), any(boolean.class));

        Folder folder = mock(Folder.class);
        when(folder.isOpen()).thenReturn(true);

        Exchange exchange = mock(Exchange.class);
        org.apache.camel.Message camelMsg = mock(org.apache.camel.Message.class);
        when(exchange.getIn()).thenReturn(camelMsg);
        when(camelMsg.getHeader(MailConstants.MAIL_COPY_TO, config.getCopyTo(), String.class)).thenReturn(null);
        when(camelMsg.getHeader(MailConstants.MAIL_MOVE_TO, config.getMoveTo(), String.class)).thenReturn(null);
        when(camelMsg.getHeader(MailConstants.MAIL_DELETE, config.isDelete(), boolean.class)).thenReturn(false);
        when(exchange.removeProperty(MailConsumer.MAIL_MESSAGE_UID)).thenReturn(null);

        ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);

        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);
        consumer.setExceptionHandler(exceptionHandler);

        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        folderField.set(consumer, folder);

        consumer.processCommit(mail, exchange);

        ArgumentCaptor<Throwable> causeCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(exceptionHandler).handleException(anyString(), any(Exchange.class), causeCaptor.capture());

        // non-MessageRemovedException must be passed through unchanged (no wrapping)
        assertInstanceOf(MessagingException.class, causeCaptor.getValue());
        assertEquals("Some other server error", causeCaptor.getValue().getMessage());
    }
}
