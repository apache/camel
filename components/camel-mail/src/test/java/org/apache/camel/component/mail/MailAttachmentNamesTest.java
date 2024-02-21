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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MailAttachmentNamesTest extends CamelTestSupport {

    public static final String UUID_EXPRESSION = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");
    private static final MailboxUser default_ = Mailbox.getOrCreateUser("default", "secret");
    private static final MailboxUser suffix = Mailbox.getOrCreateUser("suffix", "secret");

    MockEndpoint resultEndpoint;
    MockEndpoint resultDefaultEndpoint;
    Session session;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        session = Mailbox.getSmtpSession();

        super.setUp();

        Mailbox.clearAll();
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);
        resultEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(5));

        resultDefaultEndpoint = getMockEndpoint("mock:resultDefault");
        resultDefaultEndpoint.expectedMinimumMessageCount(1);
        resultDefaultEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(5));
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] { new RouteBuilder() {
            public void configure() {
                from(james.uriPrefix(Protocol.pop3)
                     + "&initialDelay=100&delay=100&generateMissingAttachmentNames=uuid&handleDuplicateAttachmentNames=uuidPrefix")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                from(suffix.uriPrefix(Protocol.pop3)
                     + "&initialDelay=100&delay=100&generateMissingAttachmentNames=uuid&handleDuplicateAttachmentNames=uuidSuffix")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                from(default_.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100").to("mock:resultDefault");
            }
        } };
    }

    @Test
    public void testAttachmentWithEmptyFilename() throws Exception {
        sendTestMessage("filename_empty.txt", james);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange.getIn(AttachmentMessage.class));
        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachmentObjects());
        assertEquals(1, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map.Entry<String, Attachment> entry
                = exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().iterator().next();
        String name = entry.getKey();
        assertTrue(isUUID(name));
    }

    @Test
    public void testAttachmentWithNoFilename() throws Exception {
        sendTestMessage("filename_none.txt", james);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(1, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map.Entry<String, Attachment> entry
                = exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().iterator().next();
        String name = entry.getKey();
        assertTrue(isUUID(name));
    }

    @Test
    public void testAttachmentWithDuplicateFilename() throws Exception {
        sendTestMessage("filename_duplicate.txt", james);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(2, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        for (Map.Entry<String, Attachment> entry : attachments.entrySet()) {
            assertEquals(48, entry.getKey().length());
            assertTrue(startsWithUUID(entry.getKey()));
            assertTrue(entry.getKey().endsWith("Capture.PNG"));
        }
    }

    /**
     * Duplicate filenames are ignored, same as handleDuplicateAttachmentNames=never
     *
     * @throws Exception
     */
    @Test
    public void testAttachmentWithDuplicateFilenameDefaultBehavior() throws Exception {
        sendTestMessage("filename_duplicate.txt", default_);

        resultDefaultEndpoint.assertIsSatisfied();
        Exchange exchange = resultDefaultEndpoint.getReceivedExchanges().get(0);
        assertEquals(1, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());
        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();

        assertNotNull(attachments.get("Capture.PNG"));
    }

    /**
     * Attachment with empty filename are ignored, same as generateMissingAttachmentNames=never
     *
     * @throws Exception
     */
    @Test
    public void testAttachmentWithEmptyFilenameDefaultBehavior() throws Exception {
        sendTestMessage("filename_empty.txt", default_);

        resultDefaultEndpoint.assertIsSatisfied();
        Exchange exchange = resultDefaultEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange.getIn(AttachmentMessage.class));
        assertNull(exchange.getIn(AttachmentMessage.class).getAttachmentObjects());
    }

    /**
     * Attachment with no filename are ignored, same as generateMissingAttachmentNames=never
     *
     * @throws Exception
     */
    @Test
    public void testAttachmentWithNoFilenameDefaultBehavior() throws Exception {
        sendTestMessage("filename_none.txt", default_);

        resultDefaultEndpoint.assertIsSatisfied();
        Exchange exchange = resultDefaultEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange.getIn(AttachmentMessage.class));
        assertNull(exchange.getIn(AttachmentMessage.class).getAttachmentObjects());
    }

    @Test
    public void testAttachmentWithDuplicateFilenameSuffix() throws Exception {
        sendTestMessage("filename_duplicate.txt", suffix);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(2, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        for (Map.Entry<String, Attachment> entry : attachments.entrySet()) {
            Pattern guidPattern = Pattern.compile("^Capture\\_" + UUID_EXPRESSION + "\\.PNG$");
            assertTrue(guidPattern.matcher(entry.getKey()).matches());
        }
    }

    @Test
    public void testAttachmentWithDuplicateFilenameSuffixMultipleDots() throws Exception {
        sendTestMessage("filename_duplicate_multiple_dots.txt", suffix);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(2, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        for (Map.Entry<String, Attachment> entry : attachments.entrySet()) {
            Pattern guidPattern = Pattern.compile("^\\.file.name\\_" + UUID_EXPRESSION + "\\.PNG$");
            assertTrue(guidPattern.matcher(entry.getKey()).matches());
        }
    }

    @Test
    public void testAttachmentWithDuplicateFilenameSuffixNoExtension() throws Exception {
        sendTestMessage("filename_duplicate_no_extension.txt", suffix);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(2, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        for (Map.Entry<String, Attachment> entry : attachments.entrySet()) {
            Pattern guidPattern = Pattern.compile("^Capture\\_" + UUID_EXPRESSION + "$");
            assertTrue(guidPattern.matcher(entry.getKey()).matches());
        }
    }

    @Test
    public void testAttachmentWithDuplicateFilenameSuffixStartsWithDot() throws Exception {
        sendTestMessage("filename_duplicate_single_dot_at_beginning.txt", suffix);

        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals(2, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        for (Map.Entry<String, Attachment> entry : attachments.entrySet()) {
            Pattern guidPattern = Pattern.compile("^\\.fileName\\_" + UUID_EXPRESSION + "$");
            assertTrue(guidPattern.matcher(entry.getKey()).matches());
        }
    }

    @Test
    public void testAttachmentWithNoDisposition() throws Exception {
        sendTestMessage("disposition_none.txt", default_);

        resultDefaultEndpoint.assertIsSatisfied();
        Exchange exchange = resultDefaultEndpoint.getReceivedExchanges().get(0);
        assertEquals(1, exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet().size());

        Map<String, Attachment> attachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        assertNotNull(attachments.get("test.jpg"));
    }

    private void sendTestMessage(String filename, MailboxUser recipient) throws MessagingException, FileNotFoundException {
        MimeMessage message = populateMimeMessage(session, filename);
        message.setRecipients(Message.RecipientType.TO, recipient.getEmail());
        Transport.send(message, recipient.getLogin(), recipient.getPassword());
    }

    private MimeMessage populateMimeMessage(Session session, String filename) throws MessagingException, FileNotFoundException {
        ClassLoader classLoader = getClass().getClassLoader();
        String path = classLoader.getResource(filename).getFile();
        InputStream is = new FileInputStream(path);
        MimeMessage message = new MimeMessage(session, is);
        return message;
    }

    private boolean isUUID(String id) {
        Pattern guidPattern = Pattern.compile("^" + UUID_EXPRESSION + "$");
        return guidPattern.matcher(id).matches();
    }

    private boolean startsWithUUID(String id) {
        Pattern guidPattern = Pattern.compile("^" + UUID_EXPRESSION + "\\_.*$");
        return guidPattern.matcher(id).matches();
    }

}
