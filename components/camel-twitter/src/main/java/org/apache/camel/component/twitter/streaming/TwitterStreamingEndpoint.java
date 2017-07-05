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
package org.apache.camel.component.twitter.streaming;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.data.StreamingType;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The Twitter Streaming component consumes twitter statuses using Streaming API.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-streaming", title = "Twitter Streaming", syntax = "twitter-streaming:streamingType",
    consumerClass = AbstractStreamingConsumerHandler.class, consumerOnly = true, label = "api,social")
public class TwitterStreamingEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The streaming type to consume.")
    @Metadata(required = "true")
    private StreamingType streamingType;

    @UriParam(description = "Can be used for a streaming filter. Multiple values can be separated with comma.", label = "consumer,filter")
    private String keywords;

    public TwitterStreamingEndpoint(String uri, String remaining, String keywords, TwitterStreamingComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        if (remaining == null) {
            throw new IllegalArgumentException(String.format("The streaming type must be specified for '%s'", uri));
        }
        this.streamingType = component.getCamelContext().getTypeConverter().convertTo(StreamingType.class, remaining);
        this.keywords = keywords;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported for twitter-streaming");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractTwitterConsumerHandler handler;
        switch (streamingType) {
        case SAMPLE:
            handler = new SampleStreamingConsumerHandler(this);
            break;
        case FILTER:
            handler = new FilterStreamingConsumerHandler(this, keywords);
            break;
        case USER:
            handler = new UserStreamingConsumerHandler(this);
            break;
        default:
            throw new IllegalArgumentException("Cannot create any consumer with uri " + getEndpointUri()
                + ". A streaming type was not provided (or an incorrect pairing was used).");
        }
        return TwitterHelper.createConsumer(processor, this, handler);
    }

    public StreamingType getStreamingType() {
        return streamingType;
    }

}
