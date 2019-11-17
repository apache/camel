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
package org.apache.camel.component.google.mail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersMessagesApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.gmail.Gmail$Users$Messages}
 * APIs.
 */
public class GmailUsersMessagesIntegrationTest extends AbstractGoogleMailTestSupport {

    // userid of the currently authenticated user
    public static final String CURRENT_USERID = "me";
    private static final Logger LOG = LoggerFactory.getLogger(GmailUsersMessagesIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleMailApiCollection.getCollection().getApiName(GmailUsersMessagesApiMethod.class).getName();

    @Test
    public void testMessages() throws Exception {

        // ==== Send test email ====
        Message testEmail = createTestEmail();
        Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is com.google.api.services.gmail.model.Message
        headers.put("CamelGoogleMail.content", testEmail);

        com.google.api.services.gmail.model.Message result = requestBodyAndHeaders("direct://SEND", null, headers);
        assertNotNull("send result", result);
        String testEmailId = result.getId();

        // ==== Search for message we just sent ====
        headers = new HashMap<>();
        headers.put("CamelGoogleMail.q", "subject:\"Hello from camel-google-mail\"");
        // using String message body for single parameter "userId"
        ListMessagesResponse listOfMessages = requestBody("direct://LIST", CURRENT_USERID);
        assertTrue(idInList(testEmailId, listOfMessages));

        // ===== trash it ====
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is String
        headers.put("CamelGoogleMail.id", testEmailId);
        requestBodyAndHeaders("direct://TRASH", null, headers);

        // ==== Search for message we just trashed ====
        headers = new HashMap<>();
        headers.put("CamelGoogleMail.q", "subject:\"Hello from camel-google-mail\"");
        // using String message body for single parameter "userId"
        listOfMessages = requestBody("direct://LIST", CURRENT_USERID);
        assertFalse(idInList(testEmailId, listOfMessages));

        // ===== untrash it ====
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is String
        headers.put("CamelGoogleMail.id", testEmailId);
        requestBodyAndHeaders("direct://UNTRASH", null, headers);

        // ==== Search for message we just trashed ====
        headers = new HashMap<>();
        headers.put("CamelGoogleMail.q", "subject:\"Hello from camel-google-mail\"");
        // using String message body for single parameter "userId"
        listOfMessages = requestBody("direct://LIST", CURRENT_USERID);
        assertTrue(idInList(testEmailId, listOfMessages));

        // ===== permanently delete it ====
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is String
        headers.put("CamelGoogleMail.id", testEmailId);
        requestBodyAndHeaders("direct://DELETE", null, headers);

        // ==== Search for message we just deleted ====
        headers = new HashMap<>();
        headers.put("CamelGoogleMail.q", "subject:\"Hello from camel-google-mail\"");
        // using String message body for single parameter "userId"
        listOfMessages = requestBody("direct://LIST", CURRENT_USERID);
        assertFalse(idInList(testEmailId, listOfMessages));
    }

    private boolean idInList(String testEmailId, ListMessagesResponse listOfMessages) {
        assertNotNull("list result", listOfMessages);
        assertTrue(!listOfMessages.getMessages().isEmpty());
        boolean foundMessage = false;
        for (Message m : listOfMessages.getMessages()) {
            if (testEmailId.equals(m.getId())) {
                return true;
            }
        }
        return false;
    }

    private Message createTestEmail() throws MessagingException, IOException {
        com.google.api.services.gmail.model.Profile profile = requestBody("google-mail://users/getProfile?inBody=userId", CURRENT_USERID);
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mm = new MimeMessage(session);
        mm.addRecipients(javax.mail.Message.RecipientType.TO, profile.getEmailAddress());
        mm.setSubject("Hello from camel-google-mail");
        mm.setContent("Camel rocks!", "text/plain");
        Message createMessageWithEmail = createMessageWithEmail(mm);
        return createMessageWithEmail;
    }

    private MimeMessage toMimeMessage(Message message) throws MessagingException {
        byte[] emailBytes = Base64.decodeBase64(message.getRaw());

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        return new MimeMessage(session, new ByteArrayInputStream(emailBytes));
    }

    private Message createMessageWithEmail(MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        email.writeTo(baos);
        String encodedEmail = Base64.encodeBase64URLSafeString(baos.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for attachments
                from("direct://ATTACHMENTS").to("google-mail://" + PATH_PREFIX + "/attachments");

                // test route for delete
                from("direct://DELETE").to("google-mail://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET").to("google-mail://" + PATH_PREFIX + "/get");

                // test route for gmailImport
                from("direct://GMAILIMPORT").to("google-mail://" + PATH_PREFIX + "/gmailImport");

                // test route for gmailImport
                from("direct://GMAILIMPORT_1").to("google-mail://" + PATH_PREFIX + "/gmailImport");

                // test route for insert
                from("direct://INSERT").to("google-mail://" + PATH_PREFIX + "/insert");

                // test route for insert
                from("direct://INSERT_1").to("google-mail://" + PATH_PREFIX + "/insert");

                // test route for list
                from("direct://LIST").to("google-mail://" + PATH_PREFIX + "/list?inBody=userId");

                // test route for modify
                from("direct://MODIFY").to("google-mail://" + PATH_PREFIX + "/modify");

                // test route for send
                from("direct://SEND").to("google-mail://" + PATH_PREFIX + "/send");

                // test route for send
                from("direct://SEND_1").to("google-mail://" + PATH_PREFIX + "/send");

                // test route for trash
                from("direct://TRASH").to("google-mail://" + PATH_PREFIX + "/trash");

                // test route for untrash
                from("direct://UNTRASH").to("google-mail://" + PATH_PREFIX + "/untrash");

            }
        };
    }
}
