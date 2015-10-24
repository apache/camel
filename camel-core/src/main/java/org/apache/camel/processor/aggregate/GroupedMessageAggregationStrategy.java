package org.apache.camel.processor.aggregate;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;

/**
 * Aggregate all {@link Message} into a single combined Exchange holding all the
 * aggregated messages in a {@link List} of {@link Message} as the message body.
 * 
 * This aggregation strategy can used in combination with @{link
 * org.apache.camel.processor.Splitter} to batch messages
 * 
 * @version
 */
public class GroupedMessageAggregationStrategy extends AbstractListAggregationStrategy<Message> {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            // for the first time we must create a new empty exchange as the
            // holder, as the outgoing exchange
            // must not be one of the grouped exchanges, as that causes a
            // endless circular reference
            oldExchange = new DefaultExchange(newExchange);
        }
        return super.aggregate(oldExchange, newExchange);
    }

    @Override
    public Message getValue(Exchange exchange) {
        return exchange.getIn();
    }
}
