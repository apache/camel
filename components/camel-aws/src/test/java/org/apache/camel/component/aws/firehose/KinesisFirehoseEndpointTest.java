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
package org.apache.camel.component.aws.firehose;

import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
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
public class KinesisFirehoseEndpointTest {

    @Mock
    private AmazonKinesisFirehose amazonKinesisFirehoseClient;

    private CamelContext camelContext;

    @Before
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("firehoseClient", amazonKinesisFirehoseClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allEndpointParams() throws Exception {
        KinesisFirehoseEndpoint endpoint = (KinesisFirehoseEndpoint) camelContext.getEndpoint("aws-kinesis-firehose://some_stream_name"
                + "?amazonKinesisFirehoseClient=#firehoseClient"
        );

        assertThat(endpoint.getClient(), is(amazonKinesisFirehoseClient));
        assertThat(endpoint.getStreamName(), is("some_stream_name"));
    }
}
