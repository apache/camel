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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class KinesisFirehoseConfiguration {

    @UriPath(description = "Name of the stream")
    @Metadata(required = "true")
    private String streamName;
    @UriParam(description = "Amazon Kinesis Firehose client to use for all requests for this endpoint")
    @Metadata(required = "true")
    private AmazonKinesisFirehose amazonKinesisFirehoseClient;
    
    public void setAmazonKinesisFirehoseClient(AmazonKinesisFirehose client) {
        this.amazonKinesisFirehoseClient = client;
    }

    public AmazonKinesisFirehose getAmazonKinesisFirehoseClient() {
        return amazonKinesisFirehoseClient;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getStreamName() {
        return streamName;
    }
}
