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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
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
public class KinesisFirehoseEndpointTest {

    @Mock
    private AmazonKinesisFirehose amazonKinesisFirehoseClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("firehoseClient", amazonKinesisFirehoseClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allEndpointParams() throws Exception {
        KinesisFirehoseEndpoint endpoint
                = (KinesisFirehoseEndpoint) camelContext.getEndpoint("aws-kinesis-firehose://some_stream_name"
                                                                     + "?amazonKinesisFirehoseClient=#firehoseClient");
        endpoint.start();

        assertEquals(amazonKinesisFirehoseClient, endpoint.getClient());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
    }

    @Test
    public void allClientCreationParams() throws Exception {
        KinesisFirehoseEndpoint endpoint
                = (KinesisFirehoseEndpoint) camelContext.getEndpoint("aws-kinesis-firehose://some_stream_name"
                                                                     + "?accessKey=xxx&secretKey=yyy&region=us-east-1");

        assertEquals(Regions.US_EAST_1.getName(), endpoint.getConfiguration().getRegion());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
    }
}
