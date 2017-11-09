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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SqsProducerTest {
    private static final String SAMPLE_MESSAGE_BODY = "this is a body";
    private static final String MESSAGE_MD5 = "00000000000000000000000000000000";
    private static final String MESSAGE_ID = "11111111111111111111111111111111";
    private static final String QUEUE_URL = "some://queue/url";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_1 = "header_name_1";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_1 = "heder_value_1";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_2 = "header_name_2";
    private static final ByteBuffer SAMPLE_MESSAGE_HEADER_VALUE_2 = ByteBuffer.wrap(new byte[10]);
    private static final String SAMPLE_MESSAGE_HEADER_NAME_3 = "header_name_3";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_3 = "heder_value_3";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_4 = "CamelHeader_1";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_4 = "testValue";
    private static final String SAMPLE_EXCHANGE_ID = "ID:whatever-the-hostname-is-32818-1506943497897-1:1:8:1:75939";
    
    @Mock
    Exchange exchange;
    @Mock
    private SqsEndpoint sqsEndpoint;
    @Mock
    private AmazonSQSClient amazonSQSClient;
    @Mock
    private Message outMessage;
    @Mock
    private Message inMessage;
    
    private SendMessageResult sendMessageResult;
    private SqsConfiguration sqsConfiguration;
    private SqsProducer underTest;

    @Before
    public void setup() throws Exception {
        underTest = new SqsProducer(sqsEndpoint);
        sendMessageResult = new SendMessageResult().withMD5OfMessageBody(MESSAGE_MD5).withMessageId(MESSAGE_ID);
        sqsConfiguration = new SqsConfiguration();
        HeaderFilterStrategy headerFilterStrategy = new SqsHeaderFilterStrategy();
        sqsConfiguration.setDelaySeconds(Integer.valueOf(0));
        sqsConfiguration.setQueueName("queueName");
        when(sqsEndpoint.getClient()).thenReturn(amazonSQSClient);
        when(sqsEndpoint.getConfiguration()).thenReturn(sqsConfiguration);
        when(amazonSQSClient.sendMessage(any(SendMessageRequest.class))).thenReturn(sendMessageResult);
        when(exchange.getIn()).thenReturn(inMessage);
        when(exchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        when(exchange.getExchangeId()).thenReturn(SAMPLE_EXCHANGE_ID);
        when(inMessage.getBody(String.class)).thenReturn(SAMPLE_MESSAGE_BODY);
        when(sqsEndpoint.getQueueUrl()).thenReturn(QUEUE_URL);
        when(sqsEndpoint.getHeaderFilterStrategy()).thenReturn(headerFilterStrategy);
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
    
    @Test
    public void isAttributeMessageStringHeaderOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1, capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getStringValue());
        assertNull(capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getBinaryValue());
    }
    
    @Test
    public void isAttributeMessageByteBufferHeaderOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2, capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getBinaryValue());
        assertNull(capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getStringValue());
    }
    
    @Test
    public void isAllAttributeMessagesOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_3, SAMPLE_MESSAGE_HEADER_VALUE_3);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_4, SAMPLE_MESSAGE_HEADER_VALUE_4);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1, capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getStringValue());
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2, capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getBinaryValue());
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_3, capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_3).getStringValue());
        assertEquals(3, capture.getValue().getMessageAttributes().size());
    }
    
    @Test
    public void itSetsMessageGroupIdUsingConstantStrategy() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("useConstant");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals("CamelSingleMessageGroup", capture.getValue().getMessageGroupId());
    }
    
    @Test
    public void itSetsMessageGroupIdUsingExchangeIdStrategy() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("useExchangeId");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_EXCHANGE_ID, capture.getValue().getMessageGroupId());
    }
    
    @Test
    public void itSetsMessageGroupIdUsingHeaderValueStrategy() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("usePropertyValue");
        when(exchange.getProperty(SqsConstants.MESSAGE_GROUP_ID_PROPERTY, String.class)).thenReturn("my-group-id");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals("my-group-id", capture.getValue().getMessageGroupId());
    }
    
    @Test
    public void itSetsMessageDedpulicationIdUsingExchangeIdStrategy() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("useConstant");
        sqsConfiguration.setMessageDeduplicationIdStrategy("useExchangeId");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_EXCHANGE_ID, capture.getValue().getMessageDeduplicationId());
    }
    
    @Test
    public void itSetsMessageDedpulicationIdUsingExchangeIdStrategyAsDefault() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("useConstant");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertEquals(SAMPLE_EXCHANGE_ID, capture.getValue().getMessageDeduplicationId());
    }
    
    @Test
    public void itDoesNotSetMessageDedpulicationIdUsingContentBasedDeduplicationStrategy() throws Exception {
        sqsConfiguration.setQueueName("queueName.fifo");
        sqsConfiguration.setMessageGroupIdStrategy("useConstant");
        sqsConfiguration.setMessageDeduplicationIdStrategy("useContentBasedDeduplication");
        
        underTest.process(exchange);
        
        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());
        
        assertNull(capture.getValue().getMessageDeduplicationId());
    }
}
