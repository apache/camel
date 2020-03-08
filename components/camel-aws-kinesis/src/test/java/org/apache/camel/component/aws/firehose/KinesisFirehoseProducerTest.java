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
package org.apache.camel.component.aws.firehose;

import java.nio.ByteBuffer;

import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
import com.amazonaws.services.kinesisfirehose.model.PutRecordRequest;
import com.amazonaws.services.kinesisfirehose.model.PutRecordResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KinesisFirehoseProducerTest {

    private static final String STREAM_NAME = "streams";
    private static final String RECORD_ID = "sample_record_id";
    private static final String SAMPLE_RECORD_BODY = "SAMPLE";
    private static final ByteBuffer SAMPLE_BUFFER = ByteBuffer.wrap(SAMPLE_RECORD_BODY.getBytes());

    @Mock
    private AmazonKinesisFirehose kinesisFirehoseClient;
    @Mock
    private KinesisFirehoseEndpoint kinesisFirehoseEndpoint;
    @Mock
    private KinesisFirehoseConfiguration kinesisFirehoseConfiguration;
    @Mock
    private Message inMessage;
    @Mock
    private PutRecordResult putRecordResult;
    @Mock(lenient = true, answer = Answers.RETURNS_DEEP_STUBS)
    private Exchange exchange;

    private KinesisFirehoseProducer kinesisFirehoseProducer;

    @Before
    public void setup() throws Exception {
        when(kinesisFirehoseEndpoint.getClient()).thenReturn(kinesisFirehoseClient);
        when(kinesisFirehoseEndpoint.getConfiguration()).thenReturn(kinesisFirehoseConfiguration);
        when(kinesisFirehoseEndpoint.getConfiguration().getStreamName()).thenReturn(STREAM_NAME);
        when(exchange.getMessage()).thenReturn(inMessage);

        when(putRecordResult.getRecordId()).thenReturn(RECORD_ID);
        when(kinesisFirehoseClient.putRecord(any(PutRecordRequest.class))).thenReturn(putRecordResult);
        kinesisFirehoseProducer = new KinesisFirehoseProducer(kinesisFirehoseEndpoint);
    }

    @Test
    public void shouldPutRecordIntoStreamWhenProcessingExchange() throws Exception {
        kinesisFirehoseProducer.process(exchange);
        verify(inMessage).setHeader(KinesisFirehoseConstants.RECORD_ID, RECORD_ID);
    }

}
