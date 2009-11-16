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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.apache.camel.component.gae.mail.GMailBinding.GMAIL_SUBJECT;
import static org.apache.camel.component.gae.mail.GMailBinding.GMAIL_TO;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/org/apache/camel/component/gae/mail/context-outbound.xml"})
public class GMailOutboundRouteBuilderTest {

    
    
    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Autowired
    private MockMailService mailService;
    
    @After
    public void tearDown() throws Exception {
        mailService.reset();
    }

    @Test
    public void testSendDefault() {
        producerTemplate.sendBody("direct:input1", "testBody"); 
        assertEquals("testSubject", mailService.getFirstMessage().getSubject());
        assertEquals("testBody", mailService.getFirstMessage().getTextBody());
        assertEquals("test1@example.org", mailService.getFirstMessage().getSender());
        assertEquals("test2@example.org", mailService.getFirstMessage().getTo().iterator().next());
    }

    @Test
    public void testSendCustomTo() {
        producerTemplate.send("direct:input1", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("testBody");
                exchange.getIn().setHeader(GMAIL_TO, "test3@example.org");
            }
        });
        assertEquals("testSubject", mailService.getFirstMessage().getSubject());
        assertEquals("testBody", mailService.getFirstMessage().getTextBody());
        assertEquals("test1@example.org", mailService.getFirstMessage().getSender());
        assertEquals("test3@example.org", mailService.getFirstMessage().getTo().iterator().next());
    }
    
    @Test
    public void testSendCustomSubject() {
        producerTemplate.send("direct:input1", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("testBody");
                exchange.getIn().setHeader(GMAIL_SUBJECT, "anotherSubject");
            }
        });
        assertEquals("anotherSubject", mailService.getFirstMessage().getSubject());
        assertEquals("testBody", mailService.getFirstMessage().getTextBody());
        assertEquals("test1@example.org", mailService.getFirstMessage().getSender());
        assertEquals("test2@example.org", mailService.getFirstMessage().getTo().iterator().next());
    }
    
}
