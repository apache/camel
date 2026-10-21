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
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.UIDFolder;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The mail to mark after the IMAP folder was opened again while the mail was routed: it is found again by its UID, in
 * the UIDVALIDITY of the poll that retrieved it.
 */
class MailConsumerMailToCommitTest {

    private static final String KEY = "mail-key";

    private final Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));
    private final UIDFolder uidFolder = (UIDFolder) folder;
    private final MemoryIdempotentRepository repository = new MemoryIdempotentRepository();
    private final Exchange exchange = mock(Exchange.class);
    private MailConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        CamelContext camelContext = mock(CamelContext.class);
        ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
        ExchangeFactory ef = mock(ExchangeFactory.class);
        when(sender.getSession()).thenReturn(Session.getInstance(Mailbox.getSessionProperties(Protocol.imap)));
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);

        MailEndpoint endpoint = new MailEndpoint();
        endpoint.setCamelContext(camelContext);
        MailConfiguration config = new MailConfiguration();
        config.configureProtocol(Protocol.imap.name());
        endpoint.setConfiguration(config);
        endpoint.setIdempotentRepository(repository);

        consumer = new MailConsumer(endpoint, mock(Processor.class), sender);
        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        folderField.set(consumer, folder);
        // the folder was opened once since the poll of the mail (open 0)
        Field opensField = MailConsumer.class.getDeclaredField("folderOpens");
        opensField.setAccessible(true);
        ((AtomicInteger) opensField.get(consumer)).set(1);

        when(folder.isOpen()).thenReturn(true);
        when(folder.getFullName()).thenReturn("INBOX");
        repository.start();
        repository.add(KEY);
        when(exchange.removeProperty(MailConsumer.MAIL_MESSAGE_UID)).thenReturn(KEY);
    }

    @Test
    void mailIsFoundAgainByItsUid() throws Exception {
        Message current = mock(Message.class);
        when(uidFolder.getUIDValidity()).thenReturn(7L);
        when(uidFolder.getMessageByUID(5L)).thenReturn(current);

        assertSame(current, consumer.mailToCommit(mock(Message.class), 5L, 7L, 0, exchange));
    }

    @Test
    void uidOfAnEarlierUidValidityIsNotLookedUp() throws Exception {
        // a later poll retrieved its mails under UIDVALIDITY 8 while this mail (UIDVALIDITY 7) was still routed
        Field validityField = MailConsumer.class.getDeclaredField("batchUidValidity");
        validityField.setAccessible(true);
        validityField.setLong(consumer, 8L);
        when(uidFolder.getUIDValidity()).thenReturn(8L);
        when(uidFolder.getMessageByUID(5L)).thenReturn(mock(Message.class));

        assertNull(consumer.mailToCommit(mock(Message.class), 5L, 7L, 0, exchange));
        verify(uidFolder, never()).getMessageByUID(5L);
    }

    @Test
    void idempotentKeyOfAMailNotFoundAgainIsSettled() throws Exception {
        when(uidFolder.getUIDValidity()).thenReturn(7L);
        when(uidFolder.getMessageByUID(5L)).thenReturn(null);

        assertNull(consumer.mailToCommit(mock(Message.class), 5L, 7L, 0, exchange));
        // idempotentRepositoryRemoveOnCommit=true (the default): the key is removed, as on commit
        assertFalse(repository.contains(KEY));
    }
}
