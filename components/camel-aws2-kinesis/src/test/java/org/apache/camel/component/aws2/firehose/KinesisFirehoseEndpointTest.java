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
package org.apache.camel.component.aws2.firehose;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class KinesisFirehoseEndpointTest {

    @Mock
    private FirehoseClient amazonKinesisFirehoseClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("firehoseClient", amazonKinesisFirehoseClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allEndpointParams() throws Exception {
        KinesisFirehose2Endpoint endpoint = (KinesisFirehose2Endpoint) camelContext
                .getEndpoint("aws2-kinesis-firehose://some_stream_name" + "?amazonKinesisFirehoseClient=#firehoseClient");
        endpoint.start();

        assertThat(endpoint.getClient(), is(amazonKinesisFirehoseClient));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
    }

    @Test
    public void allClientCreationParams() throws Exception {
        KinesisFirehose2Endpoint endpoint = (KinesisFirehose2Endpoint) camelContext
                .getEndpoint("aws2-kinesis-firehose://some_stream_name" + "?accessKey=xxx&secretKey=yyy&region=us-east-1");

        assertThat(endpoint.getConfiguration().getRegion(), is(Region.US_EAST_1.id()));
        assertThat(endpoint.getConfiguration().getAccessKey(), is("xxx"));
        assertThat(endpoint.getConfiguration().getSecretKey(), is("yyy"));
        assertThat(endpoint.getConfiguration().getStreamName(), is("some_stream_name"));
    }
}
