package org.apache.camel.component.twitter.directmessage;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * The Twitter Direct Message Component consumes/produces user's direct messages.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-directmessage", title = "Twitter Direct Message", syntax = "twitter-directmessage:endpointId", consumerClass = DirectMessageConsumerHandler.class, label = "api,social")
public class TwitterDirectMessageEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The endpoint ID (not used).")
    @Metadata(required = "true")
    private String endpointId;

    public TwitterDirectMessageEndpoint(String uri, String remaining, TwitterDirectMessageComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        this.endpointId = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (getProperties().getUser() == null || getProperties().getUser().trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Producer type set to DIRECT MESSAGE but no recipient user was set.");
        } else {
            return new DirectMessageProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = TwitterHelper.createConsumer(processor, this, new DirectMessageConsumerHandler(this));
        return answer;
    }

}
