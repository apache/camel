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
package org.apache.camel.component.aws2.ses;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SesComponentSpringTest extends CamelSpringTestSupport {

    private AmazonSESClientMock sesClient;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        sesClient = context.getRegistry().lookupByNameAndType("amazonSESClient", AmazonSESClientMock.class);
    }

    @Test
    public void sendInOnlyMessageUsingUrlOptions() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertEquals("1", exchange.getIn().getHeader(Ses2Constants.MESSAGE_ID));

        SendEmailRequest sendEmailRequest = sesClient.getSendEmailRequest();
        assertEquals("from@example.com", sendEmailRequest.source());
        assertEquals("bounce@example.com", sendEmailRequest.returnPath());
        assertEquals("This is my message text.", getBody(sendEmailRequest));
    }

    @Test
    public void sendInOutMessageUsingUrlOptions() throws Exception {
        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertEquals("1", exchange.getMessage().getHeader(Ses2Constants.MESSAGE_ID));
    }

    @Test
    public void sendRawMessage() throws Exception {
        final MockMessage mess = new MockMessage();
        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(mess);
            }
        });

        assertEquals("1", exchange.getMessage().getHeader(Ses2Constants.MESSAGE_ID));

        SendRawEmailRequest sendRawEmailRequest = sesClient.getSendRawEmailRequest();
        assertEquals("from@example.com", sendRawEmailRequest.source());
    }

    @Test
    public void sendMessageUsingMessageHeaders() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
                exchange.getIn().setHeader(Ses2Constants.FROM, "anotherFrom@example.com");
                exchange.getIn().setHeader(Ses2Constants.TO, Arrays.asList("anotherTo1@example.com", "anotherTo2@example.com"));
                exchange.getIn().setHeader(Ses2Constants.RETURN_PATH, "anotherBounce@example.com");
                exchange.getIn().setHeader(Ses2Constants.REPLY_TO_ADDRESSES, Arrays.asList("anotherReplyTo1@example.com", "anotherReplyTo2@example.com"));
                exchange.getIn().setHeader(Ses2Constants.SUBJECT, "anotherSubject");
            }
        });

        assertEquals("1", exchange.getIn().getHeader(Ses2Constants.MESSAGE_ID));

        SendEmailRequest sendEmailRequest = sesClient.getSendEmailRequest();
        assertEquals("anotherFrom@example.com", sendEmailRequest.source());
        assertEquals(2, getTo(sendEmailRequest).size());
        assertTrue(getTo(sendEmailRequest).contains("anotherTo1@example.com"));
        assertTrue(getTo(sendEmailRequest).contains("anotherTo2@example.com"));
        assertEquals("anotherBounce@example.com", sendEmailRequest.returnPath());
        assertEquals(2, sendEmailRequest.replyToAddresses().size());
        assertTrue(sendEmailRequest.replyToAddresses().contains("anotherReplyTo1@example.com"));
        assertTrue(sendEmailRequest.replyToAddresses().contains("anotherReplyTo2@example.com"));
        assertEquals("anotherSubject", getSubject(sendEmailRequest));
        assertEquals("This is my message text.", getBody(sendEmailRequest));
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/ses/SESComponentSpringTest-context.xml");
    }

    private String getBody(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.message().body().text().data();
    }

    private String getSubject(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.message().subject().data();
    }

    private List<String> getTo(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.destination().toAddresses();
    }

    private List<String> getTo(SendRawEmailRequest sendEmailRequest) {
        return sendEmailRequest.destinations();
    }
}
