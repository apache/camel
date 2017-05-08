package org.apache.camel.component.twitter.search;

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
 * The Twitter Search component consumes search results.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-search", title = "Twitter Search", syntax = "twitter-search:endpointId", consumerClass = SearchConsumerHandler.class, label = "api,social")
public class TwitterSearchEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The endpoint ID (not used).")
    @Metadata(required = "true")
    private String endpointId;

    public TwitterSearchEndpoint(String uri, String remaining, TwitterSearchComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        this.endpointId = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SearchProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        boolean hasNoKeywords = getProperties().getKeywords() == null
            || getProperties().getKeywords().trim().isEmpty();
        if (hasNoKeywords) {
            throw new IllegalArgumentException("Type set to SEARCH but no keywords were provided.");
        } else {
            return TwitterHelper.createConsumer(processor, this, new SearchConsumerHandler(this));
        }
    }

}
