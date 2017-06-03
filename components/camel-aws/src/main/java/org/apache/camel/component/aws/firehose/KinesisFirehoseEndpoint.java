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
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws.kinesis.KinesisConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The aws-kinesis-firehose component is used for producing Amazon's Kinesis Firehose streams.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "aws-kinesis-firehose", title = "AWS Kinesis Firehose", syntax = "aws-kinesis-firehose:streamName",
    producerOnly = true, label = "cloud,messaging")
public class KinesisFirehoseEndpoint extends DefaultEndpoint {

    @UriPath(description = "Name of the stream")
    @Metadata(required = "true")
    private String streamName;
    @UriParam(description = "Amazon Kinesis Firehose client to use for all requests for this endpoint")
    @Metadata(required = "true")
    private AmazonKinesisFirehose amazonKinesisFirehoseClient;

    public KinesisFirehoseEndpoint(String uri, String streamName, KinesisFirehoseComponent component) {
        super(uri, component);
        this.streamName = streamName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisFirehoseProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume messages from this endpoint");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setAmazonKinesisFirehoseClient(AmazonKinesisFirehose client) {
        this.amazonKinesisFirehoseClient = client;
    }

    public AmazonKinesisFirehose getClient() {
        return amazonKinesisFirehoseClient;
    }

    public String getStreamName() {
        return streamName;
    }
}