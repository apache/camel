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
package org.apache.camel.component.aws.kinesis;

import java.nio.ByteBuffer;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.amazonaws.services.kinesis.model.PutRecordResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KinesisProducerTest {
    private static final String SHARD_ID = "SHARD145";
    private static final String SEQUENCE_NUMBER = "SEQ123";
    private static final String STREAM_NAME = "streams";
    private static final String SAMPLE_RECORD_BODY = "SAMPLE";
    private static final String PARTITION_KEY = "partition";
    private static final ByteBuffer SAMPLE_BUFFER = ByteBuffer.wrap(SAMPLE_RECORD_BODY.getBytes());

    @Mock
    private AmazonKinesis kinesisClient;
    @Mock
    private KinesisEndpoint kinesisEndpoint;
    @Mock
    private KinesisConfiguration kinesisConfiguration;
    @Mock
    private Message outMessage;
    @Mock
    private Message inMessage;
    @Mock
    private PutRecordResult putRecordResult;
    @Mock(lenient = true, answer = Answers.RETURNS_DEEP_STUBS)
    private Exchange exchange;

    private KinesisProducer kinesisProducer;

    @BeforeEach
    public void setup() throws Exception {
        when(kinesisEndpoint.getClient()).thenReturn(kinesisClient);
        when(kinesisEndpoint.getConfiguration()).thenReturn(kinesisConfiguration);
        when(kinesisEndpoint.getConfiguration().getStreamName()).thenReturn(STREAM_NAME);

        when(exchange.getMessage()).thenReturn(inMessage);

        when(exchange.getIn().getBody(ByteBuffer.class)).thenReturn(SAMPLE_BUFFER);
        when(exchange.getIn().getHeader(KinesisConstants.PARTITION_KEY)).thenReturn(PARTITION_KEY);

        when(putRecordResult.getSequenceNumber()).thenReturn(SEQUENCE_NUMBER);
        when(putRecordResult.getShardId()).thenReturn(SHARD_ID);

        when(kinesisClient.putRecord(any(PutRecordRequest.class))).thenReturn(putRecordResult);

        kinesisProducer = new KinesisProducer(kinesisEndpoint);
    }

    @Test
    public void shouldPutRecordInRightStreamWhenProcessingExchange() throws Exception {
        kinesisProducer.process(exchange);

        ArgumentCaptor<PutRecordRequest> capture = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesisClient).putRecord(capture.capture());
        PutRecordRequest request = capture.getValue();
        ByteBuffer byteBuffer = request.getData();
        byte[] actualArray = byteBuffer.array();
        byte[] sampleArray = SAMPLE_BUFFER.array();
        assertEquals(sampleArray, actualArray);
        assertEquals(STREAM_NAME, request.getStreamName());
    }

    @Test
    public void shouldHaveProperHeadersWhenSending() throws Exception {
        String seqNoForOrdering = "1851";
        when(exchange.getIn().getHeader(KinesisConstants.SEQUENCE_NUMBER)).thenReturn(seqNoForOrdering);

        kinesisProducer.process(exchange);

        ArgumentCaptor<PutRecordRequest> capture = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesisClient).putRecord(capture.capture());
        PutRecordRequest request = capture.getValue();

        assertEquals(PARTITION_KEY, request.getPartitionKey());
        assertEquals(seqNoForOrdering, request.getSequenceNumberForOrdering());
        verify(inMessage).setHeader(KinesisConstants.SEQUENCE_NUMBER, SEQUENCE_NUMBER);
        verify(inMessage).setHeader(KinesisConstants.SHARD_ID, SHARD_ID);
    }
}
