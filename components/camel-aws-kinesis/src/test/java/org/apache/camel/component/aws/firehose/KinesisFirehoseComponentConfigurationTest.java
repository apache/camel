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

import com.amazonaws.Protocol;
import com.amazonaws.regions.Regions;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KinesisFirehoseComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithAccessAndSecretKey() throws Exception {
        KinesisFirehoseComponent component = context.getComponent("aws-kinesis-firehose", KinesisFirehoseComponent.class);
        KinesisFirehoseEndpoint endpoint = (KinesisFirehoseEndpoint) component
                .createEndpoint("aws-kinesis-firehose://some_stream_name?accessKey=xxxxx&secretKey=yyyyy");

        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals("xxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        KinesisFirehoseComponent component = context.getComponent("aws-kinesis-firehose", KinesisFirehoseComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        KinesisFirehoseEndpoint endpoint
                = (KinesisFirehoseEndpoint) component.createEndpoint("aws-kinesis-firehose://some_stream_name");

        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        KinesisFirehoseComponent component = context.getComponent("aws-kinesis-firehose", KinesisFirehoseComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        KinesisFirehoseEndpoint endpoint = (KinesisFirehoseEndpoint) component
                .createEndpoint("aws-kinesis-firehose://some_stream_name?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        KinesisFirehoseComponent component = context.getComponent("aws-kinesis-firehose", KinesisFirehoseComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        KinesisFirehoseEndpoint endpoint = (KinesisFirehoseEndpoint) component.createEndpoint(
                "aws-kinesis-firehose://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }

    @Test
    public void createEndpointWithAutoDiscoverClientFalse() throws Exception {
        KinesisFirehoseComponent component = context.getComponent("aws-kinesis-firehose", KinesisFirehoseComponent.class);
        KinesisFirehoseEndpoint endpoint = (KinesisFirehoseEndpoint) component.createEndpoint(
                "aws-kinesis-firehose://some_stream_name?accessKey=xxxxx&secretKey=yyyyy&autoDiscoverClient=false");

        assertEquals("some_stream_name", endpoint.getConfiguration().getStreamName());
        assertEquals("xxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals(false, endpoint.getConfiguration().isAutoDiscoverClient());
    }
}
