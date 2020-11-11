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
package org.apache.camel.component.aws.sqs;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    private SqsConfiguration sqsConfiguration;
    private SqsProducer underTest;

    @BeforeEach
    public void setup() throws Exception {
        sqsConfiguration = new SqsConfiguration();
        sqsConfiguration.setDelaySeconds(0);
        sqsConfiguration.setQueueName("queueName");
        SendMessageResult sendMessageResult
                = new SendMessageResult().withMD5OfMessageBody(MESSAGE_MD5).withMessageId(MESSAGE_ID);
        lenient().when(amazonSQSClient.sendMessage(any(SendMessageRequest.class))).thenReturn(sendMessageResult);
        lenient().when(sqsEndpoint.getClient()).thenReturn(amazonSQSClient);
        lenient().when(sqsEndpoint.getConfiguration()).thenReturn(sqsConfiguration);
        lenient().when(sqsEndpoint.getQueueUrl()).thenReturn(QUEUE_URL);
        lenient().when(sqsEndpoint.getHeaderFilterStrategy()).thenReturn(new SqsHeaderFilterStrategy());
        lenient().when(exchange.getMessage()).thenReturn(inMessage);
        lenient().when(exchange.getIn()).thenReturn(inMessage);
        lenient().when(exchange.getExchangeId()).thenReturn(SAMPLE_EXCHANGE_ID);
        lenient().when(inMessage.getBody(String.class)).thenReturn(SAMPLE_MESSAGE_BODY);
        underTest = new SqsProducer(sqsEndpoint);
    }

    @Test
    public void translateAttributes() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("key1", null);
        headers.put("key2", "");
        headers.put("key3", "value3");

        Map<String, MessageAttributeValue> translateAttributes = underTest.translateAttributes(headers, exchange);

        assertEquals(1, translateAttributes.size());
        assertEquals("String", translateAttributes.get("key3").getDataType());
        assertEquals("value3", translateAttributes.get("key3").getStringValue());
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
        lenient().when(inMessage.getHeader(SqsConstants.DELAY_HEADER, Integer.class)).thenReturn(Integer.valueOf(2000));
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
        Map<String, Object> headers = new HashMap<>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());

        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1,
                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getStringValue());
        assertNull(capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getBinaryValue());
    }

    @Test
    public void isAttributeMessageByteBufferHeaderOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());

        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2,
                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getBinaryValue());
        assertNull(capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getStringValue());
    }

    @Test
    public void isAllAttributeMessagesOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_3, SAMPLE_MESSAGE_HEADER_VALUE_3);
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_4, SAMPLE_MESSAGE_HEADER_VALUE_4);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<SendMessageRequest> capture = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(amazonSQSClient).sendMessage(capture.capture());

        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1,
                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1).getStringValue());
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2,
                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2).getBinaryValue());
        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_3,
                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_3).getStringValue());
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
    public void itFailsWhenFifoQueueAndNoMessageGroupIdStrategySet() {
        try {
            sqsConfiguration.setQueueName("queueName.fifo");
            SqsProducer invalidProducer = new SqsProducer(sqsEndpoint);

            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().startsWith("messageGroupIdStrategy must be set for FIFO queues"),
                    "Bad error message: " + e.getMessage());
        }
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
