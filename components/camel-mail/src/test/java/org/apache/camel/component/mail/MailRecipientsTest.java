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

import javax.mail.Message;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for recipients (To, CC, BCC)
 */
public class MailRecipientsTest extends CamelTestSupport {

    @Test
    public void testMultiRecipients() throws Exception {
        Mailbox.clearAll();

        sendBody("direct:a", "Camel does really rock");

        Mailbox inbox = Mailbox.get("camel@riders.org");
        Message msg = inbox.get(0);
        assertEquals("you@apache.org", msg.getFrom()[0].toString());
        assertEquals("camel@riders.org", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("easy@riders.org", msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals("me@you.org", msg.getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("someone@somewhere.org", msg.getRecipients(Message.RecipientType.BCC)[0].toString());

        inbox = Mailbox.get("easy@riders.org");
        msg = inbox.get(0);
        assertEquals("you@apache.org", msg.getFrom()[0].toString());
        assertEquals("camel@riders.org", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("easy@riders.org", msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals("me@you.org", msg.getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("someone@somewhere.org", msg.getRecipients(Message.RecipientType.BCC)[0].toString());

        inbox = Mailbox.get("me@you.org");
        msg = inbox.get(0);
        assertEquals("you@apache.org", msg.getFrom()[0].toString());
        assertEquals("camel@riders.org", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("easy@riders.org", msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals("me@you.org", msg.getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("someone@somewhere.org", msg.getRecipients(Message.RecipientType.BCC)[0].toString());

        inbox = Mailbox.get("someone@somewhere.org");
        msg = inbox.get(0);
        assertEquals("you@apache.org", msg.getFrom()[0].toString());
        assertEquals("camel@riders.org", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("easy@riders.org", msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals("me@you.org", msg.getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("someone@somewhere.org", msg.getRecipients(Message.RecipientType.BCC)[0].toString());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // all the recipients of this mail are:
                // to: camel@riders.org , easy@riders.org
                // cc: me@you.org
                // bcc: someone@somewhere.org
                String recipients = "&to=camel@riders.org,easy@riders.org&cc=me@you.org&bcc=someone@somewhere.org";

                from("direct:a").to("smtp://you@mymailserver.com?password=secret&from=you@apache.org" + recipients);
                // END SNIPPET: e1
            }
        };
    }
}
