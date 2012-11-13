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

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.SearchTerm;

import junit.framework.TestCase;
import org.jvnet.mock_javamail.Mailbox;

import static org.apache.camel.component.mail.SearchTermBuilder.Op.or;

/**
 *
 */
public class SearchTermBuilderTest extends TestCase {

    public void testSearchTermBuilderFromAndSubject() throws Exception {
        SearchTermBuilder build = new SearchTermBuilder();
        SearchTerm st = build.from("someone@somewhere.com").subject("Camel").build();

        assertNotNull(st);

        // create dummy message
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();

        MimeMessage msg = new MimeMessage(sender.getSession());
        msg.setSubject("Yeah Camel rocks");
        msg.setText("Apache Camel is a cool project. Have a fun ride.");
        msg.setFrom(new InternetAddress("someone@somewhere.com"));
        assertTrue("Should match message", st.match(msg));

        MimeMessage msg2 = new MimeMessage(sender.getSession());
        msg2.setSubject("Apache Camel is fantastic");
        msg2.setText("I like Camel.");
        msg2.setFrom(new InternetAddress("donotreply@somewhere.com"));
        assertFalse("Should not match message, as from doesn't match", st.match(msg2));
    }

    public void testSearchTermBuilderFromOrSubject() throws Exception {
        SearchTermBuilder build = new SearchTermBuilder();
        SearchTerm st = build.subject("Camel").from(or, "admin@apache.org").build();

        assertNotNull(st);

        // create dummy message
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();

        MimeMessage msg = new MimeMessage(sender.getSession());
        msg.setSubject("Yeah Camel rocks");
        msg.setText("Apache Camel is a cool project. Have a fun ride.");
        msg.setFrom(new InternetAddress("someone@somewhere.com"));
        assertTrue("Should match message", st.match(msg));

        MimeMessage msg2 = new MimeMessage(sender.getSession());
        msg2.setSubject("Beware");
        msg2.setText("This is from the administrator.");
        msg2.setFrom(new InternetAddress("admin@apache.org"));
        assertTrue("Should match message, as its from admin", st.match(msg2));
    }

    public void testComparison() throws Exception {
        assertEquals(1, SearchTermBuilder.Comparison.LE.asNum());
        assertEquals(2, SearchTermBuilder.Comparison.LT.asNum());
        assertEquals(3, SearchTermBuilder.Comparison.EQ.asNum());
        assertEquals(4, SearchTermBuilder.Comparison.NE.asNum());
        assertEquals(5, SearchTermBuilder.Comparison.GT.asNum());
        assertEquals(6, SearchTermBuilder.Comparison.GE.asNum());
    }

}
