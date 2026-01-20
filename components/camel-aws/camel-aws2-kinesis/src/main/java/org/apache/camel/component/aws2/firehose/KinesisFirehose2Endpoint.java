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

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.firehose.client.KinesisFirehoseClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.firehose.FirehoseClient;

import static software.amazon.awssdk.core.SdkSystemSetting.CBOR_ENABLED;

/**
 * Produce data to AWS Kinesis Firehose streams.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-kinesis-firehose", title = "AWS Kinesis Firehose",
             syntax = "aws2-kinesis-firehose:streamName", producerOnly = true, category = {
                     Category.CLOUD,
                     Category.MESSAGING },
             headersClass = KinesisFirehose2Constants.class)
public class KinesisFirehose2Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private KinesisFirehose2Configuration configuration;

    private FirehoseClient kinesisFirehoseClient;

    public KinesisFirehose2Endpoint(String uri, KinesisFirehose2Configuration configuration,
                                    KinesisFirehose2Component component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisFirehose2Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume messages from this endpoint");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!configuration.isCborEnabled()) {
            System.setProperty(CBOR_ENABLED.property(), "false");
        }
        kinesisFirehoseClient = configuration.getAmazonKinesisFirehoseClient() != null
                ? configuration.getAmazonKinesisFirehoseClient()
                : KinesisFirehoseClientFactory.getKinesisFirehoseClient(configuration);

    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonKinesisFirehoseClient())) {
            if (kinesisFirehoseClient != null) {
                kinesisFirehoseClient.close();
            }
        }
        if (!configuration.isCborEnabled()) {
            System.clearProperty(CBOR_ENABLED.property());
        }
        super.doStop();
    }

    public FirehoseClient getClient() {
        return kinesisFirehoseClient;
    }

    public KinesisFirehose2Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "firehose";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getStreamName() != null) {
            return Map.of("stream", configuration.getStreamName());
        }
        return null;
    }
}
