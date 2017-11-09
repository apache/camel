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
package org.apache.camel.component.aws.kinesis;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class KinesisEndpointTest {

    @Mock
    private AmazonKinesis amazonKinesisClient;

    private CamelContext camelContext;

    @Before
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("kinesisClient", amazonKinesisClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allTheEndpointParams() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                + "?amazonKinesisClient=#kinesisClient"
                + "&maxResultsPerRequest=101"
                + "&iteratorType=latest"
                + "&shardId=abc"
                + "&sequenceNumber=123"
        );

        assertThat(endpoint.getClient(), is(amazonKinesisClient));
        assertThat(endpoint.getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getIteratorType(), is(ShardIteratorType.LATEST));
        assertThat(endpoint.getMaxResultsPerRequest(), is(101));
        assertThat(endpoint.getSequenceNumber(), is("123"));
        assertThat(endpoint.getShardId(), is("abc"));
    }

    @Test
    public void onlyRequiredEndpointParams() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                + "?amazonKinesisClient=#kinesisClient"
        );

        assertThat(endpoint.getClient(), is(amazonKinesisClient));
        assertThat(endpoint.getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getIteratorType(), is(ShardIteratorType.TRIM_HORIZON));
        assertThat(endpoint.getMaxResultsPerRequest(), is(1));
    }

    @Test
    public void afterSequenceNumberRequiresSequenceNumber() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                + "?amazonKinesisClient=#kinesisClient"
                + "&iteratorType=AFTER_SEQUENCE_NUMBER"
                + "&shardId=abc"
                + "&sequenceNumber=123"
        );

        assertThat(endpoint.getClient(), is(amazonKinesisClient));
        assertThat(endpoint.getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getIteratorType(), is(ShardIteratorType.AFTER_SEQUENCE_NUMBER));
        assertThat(endpoint.getShardId(), is("abc"));
        assertThat(endpoint.getSequenceNumber(), is("123"));
    }

    @Test
    public void atSequenceNumberRequiresSequenceNumber() throws Exception {
        KinesisEndpoint endpoint = (KinesisEndpoint) camelContext.getEndpoint("aws-kinesis://some_stream_name"
                + "?amazonKinesisClient=#kinesisClient"
                + "&iteratorType=AT_SEQUENCE_NUMBER"
                + "&shardId=abc"
                + "&sequenceNumber=123"
        );

        assertThat(endpoint.getClient(), is(amazonKinesisClient));
        assertThat(endpoint.getStreamName(), is("some_stream_name"));
        assertThat(endpoint.getIteratorType(), is(ShardIteratorType.AT_SEQUENCE_NUMBER));
        assertThat(endpoint.getShardId(), is("abc"));
        assertThat(endpoint.getSequenceNumber(), is("123"));
    }
}