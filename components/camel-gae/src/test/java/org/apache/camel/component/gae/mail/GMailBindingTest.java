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

public class GMailBindingTest {

    private static GMailBinding binding;

    private static GMailEndpoint endpoint;
    
    private Exchange exchange;
    
    private Message message;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        binding = new GMailBinding();
        endpoint = createEndpoint("gmail:user1@gmail.com?to=user2@gmail.com&subject=test");
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
