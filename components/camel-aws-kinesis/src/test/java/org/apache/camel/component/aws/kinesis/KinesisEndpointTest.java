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

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class KinesisEndpointTest {

    @Mock
    private AmazonKinesis amazonKinesisClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("kinesisClient", amazonKinesisClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allTheEndpointParams() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                                                                              + "?amazonKinesisClient=#kinesisClient"
                                                                              + "&maxResultsPerRequest=101"
                                                                              + "&iteratorType=latest"
                                                                              + "&shardId=abc"
                                                                              + "&sequenceNumber=123");

        assertEquals(amazonKinesisClient, endpoint.getConfiguration().getAmazonKinesisClient());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals(ShardIteratorType.LATEST, endpoint.getConfiguration().getIteratorType());
        assertEquals(101, endpoint.getConfiguration().getMaxResultsPerRequest());
        assertEquals("123", endpoint.getConfiguration().getSequenceNumber());
        assertEquals("abc", endpoint.getConfiguration().getShardId());
    }

    @Test
    public void onlyRequiredEndpointParams() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                                                                              + "?amazonKinesisClient=#kinesisClient");

        assertEquals(amazonKinesisClient, endpoint.getConfiguration().getAmazonKinesisClient());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals(ShardIteratorType.TRIM_HORIZON, endpoint.getConfiguration().getIteratorType());
        assertEquals(1, endpoint.getConfiguration().getMaxResultsPerRequest());
    }

    @Test
    public void afterSequenceNumberRequiresSequenceNumber() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                                                                              + "?amazonKinesisClient=#kinesisClient"
                                                                              + "&iteratorType=AFTER_SEQUENCE_NUMBER"
                                                                              + "&shardId=abc"
                                                                              + "&sequenceNumber=123");

        assertEquals(amazonKinesisClient, endpoint.getConfiguration().getAmazonKinesisClient());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals(ShardIteratorType.AFTER_SEQUENCE_NUMBER, endpoint.getConfiguration().getIteratorType());
        assertEquals("abc", endpoint.getConfiguration().getShardId());
        assertEquals("123", endpoint.getConfiguration().getSequenceNumber());
    }

    @Test
    public void atSequenceNumberRequiresSequenceNumber() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                                                                              + "?amazonKinesisClient=#kinesisClient"
                                                                              + "&iteratorType=AT_SEQUENCE_NUMBER"
                                                                              + "&shardId=abc"
                                                                              + "&sequenceNumber=123");

        assertEquals(amazonKinesisClient, endpoint.getConfiguration().getAmazonKinesisClient());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals(ShardIteratorType.AT_SEQUENCE_NUMBER, endpoint.getConfiguration().getIteratorType());
        assertEquals("abc", endpoint.getConfiguration().getShardId());
        assertEquals("123", endpoint.getConfiguration().getSequenceNumber());
    }
}
