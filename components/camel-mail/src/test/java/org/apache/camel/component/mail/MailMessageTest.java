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
package org.apache.camel.component.mail;

import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

/**
 * @version $Revision$
 */
public class MailMessageTest extends ContextTestSupport {
    private Session mailSession;
    private MimeMessage mimeMessage;
    private MailEndpoint endpoint;
    private String body = "Hello World!";

    public void testMailMessageHandlesMultipleHeaders() throws Exception {
        mimeMessage.setRecipients(Message.RecipientType.TO, new Address[] {new InternetAddress("foo@localhost"), new InternetAddress("bar@localhost")});

        MailExchange exchange = endpoint.createExchange(mimeMessage);
        MailMessage in = exchange.getIn();

        assertEquals("mail body", body, in.getBody());

        String to = in.getHeader("TO", String.class);
        assertEquals("should have 2 receivers", "foo@localhost, bar@localhost", to);
    }

    public void testMailMessageHandlesSingleHeader() throws Exception {
        mimeMessage.setRecipients(Message.RecipientType.TO, new Address[] {new InternetAddress("frank@localhost")});

        MailExchange exchange = endpoint.createExchange(mimeMessage);
        MailMessage in = exchange.getIn();
        Object header = in.getHeader("TO");
        String value = assertIsInstanceOf(String.class, header);
        assertEquals("value", "frank@localhost", value);

        assertEquals("body", body, in.getBody());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        endpoint = resolveMandatoryEndpoint("pop3://someone@myhost:30/subject");

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        mailSession = Session.getInstance(properties, null);

        mimeMessage = new MimeMessage(mailSession);
        mimeMessage.setText(body);
    }

    @Override
    protected MailEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(MailEndpoint.class, endpoint);
    }
}
