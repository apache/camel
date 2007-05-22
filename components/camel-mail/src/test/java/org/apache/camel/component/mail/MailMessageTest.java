/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.util.ObjectHelper;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * @version $Revision: 1.1 $
 */
public class MailMessageTest extends ContextTestSupport {
    protected Session mailSession;
    protected MimeMessage mimeMessage;
    protected MailEndpoint endpoint;
    protected String body = "Hello World!";

    public void testMailMessageHandlesMultipleHeaders() throws Exception {
        mimeMessage.setRecipients(Message.RecipientType.TO, new Address[]{
                new InternetAddress("james@localhost"), new InternetAddress("bar@localhost")
        });

        MailExchange exchange = endpoint.createExchange(mimeMessage);
        MailMessage in = exchange.getIn();
        String value = in.getHeader("TO", String.class);
        assertEquals("value", "james@localhost, bar@localhost", value);
/*
        String[] values = assertIsInstanceOf(String[].class, header);
        log.debug("Found values: " + ObjectHelper.asString(values));
        assertEquals("Size", 2, values.length);
        assertEquals("values[0]", "james@localhost", values[0]);
        assertEquals("values[1]", "bar@localhost", values[1]);
*/

        assertEquals("body", body, in.getBody());
    }

    public void testMailMessageHandlesSingleHeader() throws Exception {
        mimeMessage.setRecipients(Message.RecipientType.TO, new Address[]{
                new InternetAddress("james@localhost")
        });

        MailExchange exchange = endpoint.createExchange(mimeMessage);
        MailMessage in = exchange.getIn();
        Object header = in.getHeader("TO");
        String value = assertIsInstanceOf(String.class, header);
        assertEquals("value", "james@localhost", value);

        assertEquals("body", body, in.getBody());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        endpoint = resolveMandatoryEndpoint("pop3://james@myhost:30/subject");

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