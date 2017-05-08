package org.apache.camel.component.twitter.streaming;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.data.StreamingType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * The Twitter Streaming component consumes twitter statuses using Streaming API.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-streaming", title = "Twitter Streaming", syntax = "twitter-streaming:streamingType", consumerClass = AbstractStreamingConsumerHandler.class, consumerOnly = true, label = "api,social")
public class TwitterStreamingEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The streaming type to consume.")
    @Metadata(required = "true")
    private StreamingType streamingType;

    public TwitterStreamingEndpoint(String uri, String remaining, TwitterStreamingComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        if (remaining == null) {
            throw new IllegalArgumentException(String.format("The streaming type must be specified for '%s'", uri));
        }
        this.streamingType = StreamingType.valueOf(remaining.toUpperCase());
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
            handler = new FilterStreamingConsumerHandler(this);
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
