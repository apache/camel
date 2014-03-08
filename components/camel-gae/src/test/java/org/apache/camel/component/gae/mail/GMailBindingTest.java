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
package org.apache.camel.component.gae.mail;

import com.google.appengine.api.mail.MailService.Message;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.gae.mail.GMailTestUtils.createEndpoint;
import static org.apache.camel.component.gae.mail.GMailTestUtils.createMessage;
import static org.apache.camel.component.gae.mail.GMailTestUtils.getCamelContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GMailBindingTest {

    private static GMailBinding binding;

    private static GMailEndpoint endpoint;
    
    private Exchange exchange;
    
    private Message message;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        binding = new GMailBinding();
        endpoint = createEndpoint("gmail:user1@gmail.com"
            + "?to=user2@gmail.com"
            + "&cc=user4@gmail.com"
            + "&bcc=user5@gmail.com"
            + "&subject=test");
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(getCamelContext());
        message = createMessage();
    }

    @Test
    public void testWriteFrom() {
        binding.writeFrom(endpoint, exchange, message);
        assertEquals("user1@gmail.com", message.getSender());
        exchange.getIn().setHeader(GMailBinding.GMAIL_SENDER, "user3@gmail.com");
        binding.writeFrom(endpoint, exchange, message);
        assertEquals("user3@gmail.com", message.getSender());
    }
    
    @Test
    public void testWriteTo() {
        binding.writeTo(endpoint, exchange, message);
        assertEquals("user2@gmail.com", message.getTo().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_TO, "user3@gmail.com");
        binding.writeTo(endpoint, exchange, message);
        assertEquals("user3@gmail.com", message.getTo().iterator().next());
    }
    
    @Test
    public void testWriteCc() {
        binding.writeCc(endpoint, exchange, message);
        assertEquals("user4@gmail.com", message.getCc().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_CC, "user3@gmail.com");
        binding.writeCc(endpoint, exchange, message);
        assertEquals("user3@gmail.com", message.getCc().iterator().next());
    }
    
    @Test
    public void testWriteBcc() {
        binding.writeBcc(endpoint, exchange, message);
        assertEquals("user5@gmail.com", message.getBcc().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_BCC, "user3@gmail.com");
        binding.writeBcc(endpoint, exchange, message);
        assertEquals("user3@gmail.com", message.getBcc().iterator().next());
    }
    
    @Test
    public void testWriteToMultiple() {
        binding.writeTo(endpoint, exchange, message);
        assertEquals("user2@gmail.com", message.getTo().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_TO, "user3@gmail.com,user4@gmail.com");
        binding.writeTo(endpoint, exchange, message);
        assertEquals(2, message.getTo().size());
        assertTrue(message.getTo().contains("user3@gmail.com"));
        assertTrue(message.getTo().contains("user4@gmail.com"));
    }
    
    @Test
    public void testWriteCcMultiple() {
        binding.writeCc(endpoint, exchange, message);
        assertEquals("user4@gmail.com", message.getCc().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_CC, "user3@gmail.com,user4@gmail.com");
        binding.writeCc(endpoint, exchange, message);
        assertEquals(2, message.getCc().size());
        assertTrue(message.getCc().contains("user3@gmail.com"));
        assertTrue(message.getCc().contains("user4@gmail.com"));
    }
    
    @Test
    public void testWriteBccMultiple() {
        binding.writeBcc(endpoint, exchange, message);
        assertEquals("user5@gmail.com", message.getBcc().iterator().next());
        exchange.getIn().setHeader(GMailBinding.GMAIL_BCC, "user3@gmail.com,user4@gmail.com");
        binding.writeBcc(endpoint, exchange, message);
        assertEquals(2, message.getBcc().size());
        assertTrue(message.getBcc().contains("user3@gmail.com"));
        assertTrue(message.getBcc().contains("user4@gmail.com"));
    }
    
    @Test
    public void testWriteSubject() {
        binding.writeSubject(endpoint, exchange, message);
        assertEquals("test", message.getSubject());
        exchange.getIn().setHeader(GMailBinding.GMAIL_SUBJECT, "another");
        binding.writeSubject(endpoint, exchange, message);
        assertEquals("another", message.getSubject());
    }
    
    @Test
    public void testWriteBody() {
        exchange.getIn().setBody("test");
        binding.writeBody(endpoint, exchange, message);
        assertEquals("test", message.getTextBody());
    }
    
}
