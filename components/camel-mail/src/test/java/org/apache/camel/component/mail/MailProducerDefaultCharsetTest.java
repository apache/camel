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

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Text sent with a contentType that has no charset goes out with the charset it was encoded in.
 */
public class MailProducerDefaultCharsetTest extends CamelTestSupport {

    private static final MailboxUser plain = Mailbox.getOrCreateUser("MailProducerDefaultCharsetTest-plain", "secret");
    private static final MailboxUser alternative
            = Mailbox.getOrCreateUser("MailProducerDefaultCharsetTest-alternative", "secret");
    private static final String TEXT = "Gr\u00fc\u00dfe aus K\u00f8benhavn";

    @Test
    public void testPlainTextWithoutCharset() throws Exception {
        Mailbox.clearAll(plain);

        template.sendBody("direct:plain", TEXT);

        Message msg = plain.getInbox().get(0);
        assertNotNull(new ContentType(msg.getContentType()).getParameter("charset"), msg.getContentType());
        assertEquals(TEXT, msg.getContent());
    }

    @Test
    public void testAlternativeWithoutCharset() throws Exception {
        Mailbox.clearAll(alternative);

        template.sendBodyAndHeader("direct:alternative", "<p>" + TEXT + "</p>", MailConstants.MAIL_ALTERNATIVE_BODY, TEXT);

        Message msg = alternative.getInbox().get(0);
        MimeMultipart mp = (MimeMultipart) msg.getContent();
        BodyPart html = mp.getBodyPart(1);
        assertNotNull(new ContentType(html.getContentType()).getParameter("charset"), html.getContentType());
        assertEquals("<p>" + TEXT + "</p>", html.getContent());
        assertEquals(TEXT, mp.getBodyPart(0).getContent());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:plain").to(plain.uriPrefix(Protocol.smtp));
                from("direct:alternative").to(alternative.uriPrefix(Protocol.smtp) + "&contentType=text/html");
            }
        };
    }
}
