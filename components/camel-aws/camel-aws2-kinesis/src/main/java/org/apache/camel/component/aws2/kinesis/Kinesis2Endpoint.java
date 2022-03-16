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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.kinesis.client.KinesisClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static software.amazon.awssdk.core.SdkSystemSetting.CBOR_ENABLED;

/**
 * Consume and produce records from and to AWS Kinesis Streams using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-kinesis", title = "AWS Kinesis", syntax = "aws2-kinesis:streamName",
             category = { Category.CLOUD, Category.MESSAGING }, headersClass = Kinesis2Constants.class)
public class Kinesis2Endpoint extends ScheduledPollEndpoint {

    @UriParam
    private Kinesis2Configuration configuration;

    private KinesisClient kinesisClient;

    public Kinesis2Endpoint(String uri, Kinesis2Configuration configuration, Kinesis2Component component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!configuration.isCborEnabled()) {
            System.setProperty(CBOR_ENABLED.property(), "false");
        }
        kinesisClient = configuration.getAmazonKinesisClient() != null
                ? configuration.getAmazonKinesisClient()
                : KinesisClientFactory.getKinesisClient(configuration).getKinesisClient();

        if ((configuration.getIteratorType().equals(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
                || configuration.getIteratorType().equals(ShardIteratorType.AT_SEQUENCE_NUMBER))
                && configuration.getSequenceNumber().isEmpty()) {
            throw new IllegalArgumentException(
                    "Sequence Number must be specified with iterator Types AFTER_SEQUENCE_NUMBER or AT_SEQUENCE_NUMBER");
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonKinesisClient())) {
            if (kinesisClient != null) {
                kinesisClient.close();
            }
        }
        if (!configuration.isCborEnabled()) {
            System.clearProperty(CBOR_ENABLED.property());
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Kinesis2Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final Kinesis2Consumer consumer = new Kinesis2Consumer(this, processor);
        consumer.setSchedulerProperties(getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    public KinesisClient getClient() {
        return kinesisClient;
    }

    public Kinesis2Configuration getConfiguration() {
        return configuration;
    }
}
