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
package org.apache.camel.component.aws.ses;

import java.util.Arrays;
import java.util.List;

import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SesComponentTest extends CamelTestSupport {

    @BindToRegistry("amazonSESClient")
    private AmazonSESClientMock sesClient = new AmazonSESClientMock();
    
    @BindToRegistry("toList")
    private List<String> toList = Arrays.asList("to1@example.com", "to2@example.com");
    
    @BindToRegistry("replyToList")
    private List<String> replyToList = Arrays.asList("replyTo1@example.com", "replyTo2@example.com");

    @Test
    public void sendInOnlyMessageUsingUrlOptions() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });
        
        assertEquals("1", exchange.getIn().getHeader(SesConstants.MESSAGE_ID));
        
        SendEmailRequest sendEmailRequest = sesClient.getSendEmailRequest();
        assertEquals("from@example.com", sendEmailRequest.getSource());
        assertEquals(2, getTo(sendEmailRequest).size());
        assertTrue(getTo(sendEmailRequest).contains("to1@example.com"));
        assertTrue(getTo(sendEmailRequest).contains("to2@example.com"));
        assertEquals("bounce@example.com", sendEmailRequest.getReturnPath());
        assertEquals(2, sendEmailRequest.getReplyToAddresses().size());
        assertTrue(sendEmailRequest.getReplyToAddresses().contains("replyTo1@example.com"));
        assertTrue(sendEmailRequest.getReplyToAddresses().contains("replyTo2@example.com"));
        assertEquals("Subject", getSubject(sendEmailRequest));
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
        
        assertEquals("1", exchange.getMessage().getHeader(SesConstants.MESSAGE_ID));
    }

    @Test
    public void sendMessageUsingMessageHeaders() throws Exception {
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
                exchange.getIn().setHeader(SesConstants.FROM, "anotherFrom@example.com");
                exchange.getIn().setHeader(SesConstants.TO,
                        Arrays.asList("anotherTo1@example.com", "anotherTo2@example.com"));
                exchange.getIn().setHeader(SesConstants.RETURN_PATH, "anotherBounce@example.com");
                exchange.getIn().setHeader(SesConstants.REPLY_TO_ADDRESSES,
                        Arrays.asList("anotherReplyTo1@example.com", "anotherReplyTo2@example.com"));
                exchange.getIn().setHeader(SesConstants.SUBJECT, "anotherSubject");
            }
        });
        
        assertEquals("1", exchange.getIn().getHeader(SesConstants.MESSAGE_ID));

        SendEmailRequest sendEmailRequest = sesClient.getSendEmailRequest();
        assertEquals("anotherFrom@example.com", sendEmailRequest.getSource());
        assertEquals(2, getTo(sendEmailRequest).size());
        assertTrue(getTo(sendEmailRequest).contains("anotherTo1@example.com"));
        assertTrue(getTo(sendEmailRequest).contains("anotherTo2@example.com"));
        assertEquals("anotherBounce@example.com", sendEmailRequest.getReturnPath());
        assertEquals(2, sendEmailRequest.getReplyToAddresses().size());
        assertTrue(sendEmailRequest.getReplyToAddresses().contains("anotherReplyTo1@example.com"));
        assertTrue(sendEmailRequest.getReplyToAddresses().contains("anotherReplyTo2@example.com"));
        assertEquals("anotherSubject", getSubject(sendEmailRequest));
        assertEquals("This is my message text.", getBody(sendEmailRequest));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("aws-ses://from@example.com"
                        + "?to=#toList"
                        + "&subject=Subject"
                        + "&returnPath=bounce@example.com"
                        + "&replyToAddresses=#replyToList"
                        + "&amazonSESClient=#amazonSESClient");
            }
        };
    }

    private String getBody(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.getMessage().getBody().getText().getData();
    }

    private String getSubject(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.getMessage().getSubject().getData();
    }

    private List<String> getTo(SendEmailRequest sendEmailRequest) {
        return sendEmailRequest.getDestination().getToAddresses();
    }
}
