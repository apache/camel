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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
import org.apache.camel.spi.IdempotentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The completion of an exchange checks and marks its mail while holding the folder lock of the consumer, but passes an
 * error of the mark to the exception handler only after it released the lock: with bridgeErrorHandler=true the handler
 * routes the error through the error handler of the route, and the next poll and the stop of the consumer must not wait
 * for that.
 */
class MailConsumerCommitErrorWithoutLockTest {

    private static final String KEY = "mail-key";

    private final IdempotentRepository repository = mock(IdempotentRepository.class);
    private final Folder folder = mock(Folder.class);
    private final Message mail = mock(Message.class);
    private final Exchange exchange = mock(Exchange.class);
    // whether the folder lock was held while the exception handler ran, one entry per call
    private final List<Boolean> lockedInHandler = new ArrayList<>();
    private MailConsumer consumer;
    private ReentrantLock folderLock;

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

        org.apache.camel.Message in = mock(org.apache.camel.Message.class);
        when(exchange.getIn()).thenReturn(in);
        when(in.getHeader(MailConstants.MAIL_DELETE, false, boolean.class)).thenReturn(false);
        when(exchange.removeProperty(MailConsumer.MAIL_MESSAGE_UID)).thenReturn(KEY);
        when(folder.getFullName()).thenReturn("INBOX");

        // a started consumer (commit does not mark the mail of a stopped one)
        consumer = new MailConsumer(endpoint, mock(Processor.class), sender) {
            @Override
            public boolean isRunAllowed() {
                return true;
            }
        };
        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        folderField.set(consumer, folder);
        Field lockField = MailConsumer.class.getDeclaredField("folderLock");
        lockField.setAccessible(true);
        folderLock = (ReentrantLock) lockField.get(consumer);

        ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);
        doAnswer(invocation -> lockedInHandler.add(folderLock.isLocked()))
                .when(exceptionHandler).handleException(any(), any(), any());
        consumer.setExceptionHandler(exceptionHandler);
    }

    @Test
    void markThatFailsIsReportedWithoutTheLock() throws Exception {
        when(folder.isOpen()).thenReturn(true);
        doThrow(new MessageRemovedException("expunged")).when(mail).setFlag(any(Flags.Flag.class), anyBoolean());

        consumer.commit(mail, 5L, 7L, 0, exchange);

        assertEquals(1, lockedInHandler.size(), "the failed mark is reported once");
        assertFalse(lockedInHandler.get(0), "the exception handler ran while the folder lock was held");
        verify(repository).remove(KEY);
    }

    @Test
    void folderThatCannotBeOpenedAgainIsReportedWithoutTheLock() throws Exception {
        // the folder was opened again since the poll of the mail, and it cannot be opened now
        Field opensField = MailConsumer.class.getDeclaredField("folderOpens");
        opensField.setAccessible(true);
        ((AtomicInteger) opensField.get(consumer)).set(1);
        when(folder.isOpen()).thenReturn(false);
        doThrow(new MessagingException("connection refused")).when(folder).open(Folder.READ_WRITE);

        consumer.commit(mail, 5L, 7L, 0, exchange);

        assertEquals(1, lockedInHandler.size(), "the failed open is reported once");
        assertFalse(lockedInHandler.get(0), "the exception handler ran while the folder lock was held");
        verify(repository).remove(KEY);
    }

    @Test
    void markThatFailsIsReportedAlsoWhenTheRepositoryThrows() throws Exception {
        when(folder.isOpen()).thenReturn(true);
        doThrow(new MessageRemovedException("expunged")).when(mail).setFlag(any(Flags.Flag.class), anyBoolean());
        IllegalStateException down = new IllegalStateException("repository down");
        doThrow(down).when(repository).remove(KEY);

        IllegalStateException thrown
                = assertThrows(IllegalStateException.class, () -> consumer.commit(mail, 5L, 7L, 0, exchange));

        assertSame(down, thrown, "the exception of the repository still propagates");
        assertEquals(1, lockedInHandler.size(), "the failed mark is reported although the repository threw");
        assertFalse(lockedInHandler.get(0), "the exception handler ran while the folder lock was held");
        assertFalse(folderLock.isLocked(), "the folder lock is released");
    }
}
