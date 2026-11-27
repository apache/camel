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
import jakarta.mail.FolderClosedException;
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
import org.apache.camel.spi.IdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When the mail of a completed exchange cannot be marked (the mail was expunged meanwhile, or the connection of the
 * folder dropped, as IMAPMessage reports it), the error is reported and the idempotent key of the mail is settled as on
 * commit (removed by default, confirmed with idempotentRepositoryRemoveOnCommit=false), so the mail is not skipped as a
 * duplicate until the repository forgets the key.
 */
class MailConsumerCommitFailedKeyTest {

    private static final String KEY = "mail-key";

    private final IdempotentRepository repository = mock(IdempotentRepository.class);
    private final ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);

    @Test
    void keyIsRemovedWhenTheMailWasExpunged() throws Exception {
        commitFailingWith(new MessageRemovedException("expunged"), true);

        verify(exceptionHandler).handleException(anyString(), any(Exchange.class), any(MessagingException.class));
        verify(repository).remove(KEY);
    }

    @Test
    void keyIsConfirmedWhenTheConnectionDroppedAndKeysAreKept() throws Exception {
        commitFailingWith(new FolderClosedException(mock(Folder.class), "connection dropped"), false);

        verify(exceptionHandler).handleException(anyString(), any(Exchange.class), any(FolderClosedException.class));
        verify(repository).confirm(KEY);
        verify(repository, never()).remove(KEY);
    }

    private void commitFailingWith(MessagingException failure, boolean removeOnCommit) throws Exception {
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
        endpoint.setIdempotentRepositoryRemoveOnCommit(removeOnCommit);

        Folder folder = mock(Folder.class);
        when(folder.isOpen()).thenReturn(true);
        Message mail = mock(Message.class);
        doThrow(failure).when(mail).setFlag(any(Flags.Flag.class), anyBoolean());
        Exchange exchange = mock(Exchange.class);
        org.apache.camel.Message in = mock(org.apache.camel.Message.class);
        when(exchange.getIn()).thenReturn(in);
        when(in.getHeader(MailConstants.MAIL_DELETE, false, boolean.class)).thenReturn(false);
        when(exchange.removeProperty(MailConsumer.MAIL_MESSAGE_UID)).thenReturn(KEY);

        MailConsumer consumer = new MailConsumer(endpoint, mock(Processor.class), sender);
        consumer.setExceptionHandler(exceptionHandler);
        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        folderField.set(consumer, folder);

        consumer.processCommit(mail, exchange);
    }
}
