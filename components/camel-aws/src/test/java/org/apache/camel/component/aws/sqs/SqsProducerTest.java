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
package org.apache.camel.component.aws.sqs;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SqsProducerTest {
    private static final String SAMPLE_MESSAGE_BODY = "this is a body";
    private static final String MESSAGE_MD5 = "00000000000000000000000000000000";
    private static final String MESSAGE_ID = "11111111111111111111111111111111";
    private static final String QUEUE_URL = "some://queue/url";
    
    Exchange exchange = mock(Exchange.class, RETURNS_DEEP_STUBS);

    @Mock private SqsEndpoint sqsEndpoint;
    @Mock private AmazonSQSClient amazonSQSClient;
    @Mock private Message outMessage;
    @Mock private Message inMessage;
    
    private SendMessageResult sendMessageResult;
    private SqsConfiguration sqsConfiguration;

    private SqsProducer underTest;

    @Before
    public void setup() throws Exception {
        underTest = new SqsProducer(sqsEndpoint);
        sendMessageResult = new SendMessageResult().withMD5OfMessageBody(MESSAGE_MD5).withMessageId(MESSAGE_ID);
        sqsConfiguration = new SqsConfiguration();
        sqsConfiguration.setDelaySeconds(Integer.valueOf(0));
        when(sqsEndpoint.getClient()).thenReturn(amazonSQSClient);
        when(sqsEndpoint.getConfiguration()).thenReturn(sqsConfiguration);
        when(amazonSQSClient.sendMessage(any(SendMessageRequest.class))).thenReturn(sendMessageResult);
        when(exchange.getOut()).thenReturn(outMessage);
        when(exchange.getIn()).thenReturn(inMessage);
        when(exchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        when(inMessage.getBody(String.class)).thenReturn(SAMPLE_MESSAGE_BODY);
        when(sqsEndpoint.getQueueUrl()).thenReturn(QUEUE_URL);
    }

    @Test
    public void itSendsTheBodyFromAnExchange() throws Exception {
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        assertEquals(SAMPLE_MESSAGE_BODY, capture.getValue().getMessageBody());
    }

    @Test
    public void itSendsTheCorrectQueueUrl() throws Exception {
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        assertEquals(QUEUE_URL, capture.getValue().getQueueUrl());
    }

    @Test
    public void itSetsTheDelayFromTheConfigurationOnTheRequest() throws Exception {
        sqsConfiguration.setDelaySeconds(Integer.valueOf(9001));
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        assertEquals(9001, capture.getValue().getDelaySeconds().intValue());
    }
    
    @Test
    public void itSetsTheDelayFromMessageHeaderOnTheRequest() throws Exception {
        when(inMessage.getHeader(SqsConstants.DELAY_HEADER, Integer.class)).thenReturn(Integer.valueOf(2000));
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        assertEquals(2000, capture.getValue().getDelaySeconds().intValue());
    }

    @Test
    public void itSetsTheMessageIdOnTheExchangeMessage() throws Exception {
        underTest.process(exchange);
        verify(inMessage).setHeader(SqsConstants.MESSAGE_ID, MESSAGE_ID);
    }

    @Test
    public void itSetsTheMd5SumOnTheExchangeMessage() throws Exception {
        underTest.process(exchange);
        verify(inMessage).setHeader(SqsConstants.MD5_OF_BODY, MESSAGE_MD5);
    }

}