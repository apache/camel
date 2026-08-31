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
import java.util.Properties;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link MailConsumer#poll()} does not throw {@link NullPointerException} in the {@code finally} catch
 * block when {@code folder.close()} throws and the {@code folder} instance field is concurrently set to null.
 *
 * <p>
 * The fix captures {@code folder} into a {@code final} local variable ({@code currentFolder}) before the try block so
 * that all accesses within the block — including the catch log — use the same snapshot. CAMEL-24567.
 */
class MailConsumerFolderNullCatchTest {

    private static final String FIELD_FOLDER = "folder";
    private static final String FIELD_STORE = "store";

    @Test
    void testFolderCloseThrowsAndFolderBecomesNullDoesNotNPE() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        Processor processor = mock(Processor.class);
        CamelContext camelContext = mock(CamelContext.class);
        ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
        ExchangeFactory ef = mock(ExchangeFactory.class);

        // use plain Session — no GreenMail static initializer needed
        Session session = Session.getInstance(new Properties());

        when(sender.getSession()).thenReturn(session);
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);

        MailEndpoint endpoint = new MailEndpoint();
        endpoint.setCamelContext(camelContext);
        MailConfiguration config = new MailConfiguration();
        config.configureProtocol("imap");
        config.setPort(3143); // arbitrary port; no real connection is made
        config.setFolderName("INBOX");
        config.setCloseFolder(true); // ensures the finally close path runs
        endpoint.setConfiguration(config);

        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);

        Field folderField = MailConsumer.class.getDeclaredField(FIELD_FOLDER);
        folderField.setAccessible(true);
        Field storeField = MailConsumer.class.getDeclaredField(FIELD_STORE);
        storeField.setAccessible(true);

        // folder.isOpen() → true so close() will be attempted
        // folder.getMessageCount() → 0 so poll completes without processing messages
        // folder.close() throws MessagingException AND nulls the instance field,
        //   simulating a concurrent disconnect() that could race with poll()
        Folder folder = mock(Folder.class);
        when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(0);
        doAnswer(inv -> {
            folderField.set(consumer, null);
            throw new MessagingException("server locked the folder");
        }).when(folder).close(anyBoolean());

        Store store = mock(Store.class);
        when(store.isConnected()).thenReturn(true);

        folderField.set(consumer, folder);
        storeField.set(consumer, store);

        // must not throw NullPointerException — CAMEL-24567
        assertDoesNotThrow(() -> consumer.poll(),
                "poll() must not throw NPE when folder is nulled while folder.close() throws");
    }
}
