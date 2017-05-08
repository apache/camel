package org.apache.camel.component.twitter.timeline;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * The Twitter Timeline component consumes twitter timeline or update the status of specific user.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-timeline", title = "Twitter Timeline", syntax = "twitter-timeline:timelineType", consumerClass = AbstractStatusConsumerHandler.class, label = "api,social")
public class TwitterTimelineEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The timeline type to produce/consume.")
    @Metadata(required = "true")
    private TimelineType timelineType;

    public TwitterTimelineEndpoint(String uri, String remaining, TwitterTimelineComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        if (remaining == null) {
            throw new IllegalArgumentException(String.format("The timeline type must be specified for '%s'", uri));
        }
        this.timelineType = TimelineType.valueOf(remaining.toUpperCase());
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (timelineType) {
        case USER:
            return new UserProducer(this);
        default:
            throw new IllegalArgumentException("Cannot create any producer with uri " + getEndpointUri()
                                               + ". A producer type was not provided (or an incorrect pairing was used).");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractTwitterConsumerHandler handler = null;
        switch (timelineType) {
        case HOME:
            handler = new HomeConsumerHandler(this);
            break;
        case MENTIONS:
            handler = new MentionsConsumerHandler(this);
            break;
        case RETWEETSOFME:
            handler = new RetweetsConsumerHandler(this);
            break;
        case USER:
            if (getProperties().getUser() == null || getProperties().getUser().trim().isEmpty()) {
                throw new IllegalArgumentException("Fetch type set to USER TIMELINE but no user was set.");
            } else {
                handler = new UserConsumerHandler(this);
                break;
            }
        default:
            break;
        }
        if (handler != null) {
            return TwitterHelper.createConsumer(processor, this, handler);
        }
        throw new IllegalArgumentException("Cannot create any consumer with uri " + getEndpointUri()
                                           + ". A consumer type was not provided (or an incorrect pairing was used).");
        
    }

    public TimelineType getTimelineType() {
        return timelineType;
    }

}
