/**
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.model.Message;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersThreadsApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.gmail.Gmail$Users$Threads}
 * APIs.
 */
public class GmailUsersThreadsIntegrationTest extends AbstractGoogleMailTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GmailUsersThreadsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleMailApiCollection.getCollection().getApiName(GmailUsersThreadsApiMethod.class).getName();

    private Message createThreadedTestEmail(String previousThreadId) throws MessagingException, IOException {
        com.google.api.services.gmail.model.Profile profile = requestBody("google-mail://users/getProfile?inBody=userId", CURRENT_USERID);
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mm = new MimeMessage(session);
        mm.addRecipients(javax.mail.Message.RecipientType.TO, profile.getEmailAddress());
        mm.setSubject("Hello from camel-google-mail");
        mm.setContent("Camel rocks!", "text/plain");
        Message createMessageWithEmail = createMessageWithEmail(mm);
        if (previousThreadId != null) {
            createMessageWithEmail.setThreadId(previousThreadId);
        }

        Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is com.google.api.services.gmail.model.Message
        headers.put("CamelGoogleMail.content", createMessageWithEmail);

        return requestBodyAndHeaders("google-mail://messages/send", null, headers);
    }

    private Message createMessageWithEmail(MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        email.writeTo(baos);
        String encodedEmail = Base64.encodeBase64URLSafeString(baos.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    @Test
    public void testList() throws Exception {
        Message m1 = createThreadedTestEmail(null);
        Message m2 = createThreadedTestEmail(m1.getThreadId());

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("CamelGoogleMail.q", "subject:\"Hello from camel-google-mail\"");

        // using String message body for single parameter "userId"
        com.google.api.services.gmail.model.ListThreadsResponse result = requestBodyAndHeaders("direct://LIST", CURRENT_USERID, headers);

        assertNotNull("list result", result);
        assertTrue(result.getThreads().size() > 0);
        LOG.debug("list: " + result);

        headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is String
        headers.put("CamelGoogleMail.id", m1.getThreadId());

        requestBodyAndHeaders("direct://DELETE", null, headers);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for delete
                from("direct://DELETE").to("google-mail://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET").to("google-mail://" + PATH_PREFIX + "/get");

                // test route for list
                from("direct://LIST").to("google-mail://" + PATH_PREFIX + "/list?inBody=userId");

                // test route for modify
                from("direct://MODIFY").to("google-mail://" + PATH_PREFIX + "/modify");

                // test route for trash
                from("direct://TRASH").to("google-mail://" + PATH_PREFIX + "/trash");

                // test route for untrash
                from("direct://UNTRASH").to("google-mail://" + PATH_PREFIX + "/untrash");

            }
        };
    }
}
