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

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link MailConsumer#poll()} handles a null {@code currentFolder} local variable safely in the
 * {@code finally} catch block. Before the fix, the block used the instance field directly without a null guard; the fix
 * uses a local variable captured before the try block, providing consistency throughout the poll. CAMEL-24567.
 */
class MailConsumerFolderNullCatchTest {

    @Test
    void testFolderCloseThrowsAndFolderBecomesNullDoesNotNPE() throws Exception {
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
        config.setCloseFolder(true); // ensures the finally close path runs
        endpoint.setConfiguration(config);

        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);

        Field folderField = MailConsumer.class.getDeclaredField("folder");
        folderField.setAccessible(true);
        Field storeField = MailConsumer.class.getDeclaredField("store");
        storeField.setAccessible(true);

        // set up a folder that:
        // 1. isOpen() returns true — so close() will be called
        // 2. getMessageCount() returns 0 — poll completes without processing messages
        // 3. close() throws MessagingException — triggers the catch block
        //    The test verifies the catch block handles gracefully even when the
        //    instance field is null (simulating the local-variable capture being null)
        Folder folder = mock(Folder.class);
        when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(0);
        doAnswer(inv -> {
            // null the instance field to exercise the null-safe name lookup in the catch block
            folderField.set(consumer, null);
            throw new MessagingException("server locked the folder");
        }).when(folder).close(anyBoolean());

        Store store = mock(Store.class);
        when(store.isConnected()).thenReturn(true);

        folderField.set(consumer, folder);
        storeField.set(consumer, store);

        // must not throw NullPointerException in the catch block — CAMEL-24567
        assertDoesNotThrow(() -> consumer.poll(),
                "poll() must not throw NPE when folder becomes null inside folder.close() catch block");
    }
}
