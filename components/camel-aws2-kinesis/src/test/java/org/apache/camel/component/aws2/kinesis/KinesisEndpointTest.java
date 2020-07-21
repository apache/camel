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
package org.apache.camel.component.aws2.kinesis;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class KinesisEndpointTest {

    @Mock
    private KinesisClient amazonKinesisClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("kinesisClient", amazonKinesisClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allTheEndpointParams() throws Exception {
        Kinesis2Endpoint endpoint = (Kinesis2Endpoint)camelContext.getEndpoint("aws2-kinesis://some_stream_name" + "?amazonKinesisClient=#kinesisClient"
                                                                               + "&maxResultsPerRequest=101" + "&iteratorType=latest" + "&shardId=abc" + "&sequenceNumber=123");

        assertThat(endpoint.getConfiguration().getAmazonKinesisClient(), is(amazonKinesisClient));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getConfiguration().getIteratorType(), is(ShardIteratorType.LATEST));
        assertThat(endpoint.getConfiguration().getMaxResultsPerRequest(), is(101));
        assertThat(endpoint.getConfiguration().getSequenceNumber(), is("123"));
        assertThat(endpoint.getConfiguration().getShardId(), is("abc"));
    }

    @Test
    public void onlyRequiredEndpointParams() throws Exception {
        Kinesis2Endpoint endpoint = (Kinesis2Endpoint)camelContext.getEndpoint("aws2-kinesis://some_stream_name" + "?amazonKinesisClient=#kinesisClient");

        assertThat(endpoint.getConfiguration().getAmazonKinesisClient(), is(amazonKinesisClient));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getConfiguration().getIteratorType(), is(ShardIteratorType.TRIM_HORIZON));
        assertThat(endpoint.getConfiguration().getMaxResultsPerRequest(), is(1));
    }

    @Test
    public void afterSequenceNumberRequiresSequenceNumber() throws Exception {
        Kinesis2Endpoint endpoint = (Kinesis2Endpoint)camelContext.getEndpoint("aws2-kinesis://some_stream_name" + "?amazonKinesisClient=#kinesisClient"
                                                                               + "&iteratorType=AFTER_SEQUENCE_NUMBER" + "&shardId=abc" + "&sequenceNumber=123");

        assertThat(endpoint.getConfiguration().getAmazonKinesisClient(), is(amazonKinesisClient));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getConfiguration().getIteratorType(), is(ShardIteratorType.AFTER_SEQUENCE_NUMBER));
        assertThat(endpoint.getConfiguration().getShardId(), is("abc"));
        assertThat(endpoint.getConfiguration().getSequenceNumber(), is("123"));
    }

    @Test
    public void atSequenceNumberRequiresSequenceNumber() throws Exception {
        Kinesis2Endpoint endpoint = (Kinesis2Endpoint)camelContext
            .getEndpoint("aws2-kinesis://some_stream_name" + "?amazonKinesisClient=#kinesisClient" + "&iteratorType=AT_SEQUENCE_NUMBER" + "&shardId=abc" + "&sequenceNumber=123");

        assertThat(endpoint.getConfiguration().getAmazonKinesisClient(), is(amazonKinesisClient));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getConfiguration().getIteratorType(), is(ShardIteratorType.AT_SEQUENCE_NUMBER));
        assertThat(endpoint.getConfiguration().getShardId(), is("abc"));
        assertThat(endpoint.getConfiguration().getSequenceNumber(), is("123"));
    }
}
