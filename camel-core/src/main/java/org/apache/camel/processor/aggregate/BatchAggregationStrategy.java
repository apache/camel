import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AbstractListAggregationStrategy;

/**
 * Aggregate body of input {@link Message} into a single combined Exchange holding all the
 * aggregated bodies in a {@link List} of type {@link Object} as the message body.
 *
 * This aggregation strategy can used in combination with {@link org.apache.camel.processor.Splitter} to batch messages
 *
 * @version
 */
public class BatchAggregationStrategy extends AbstractListAggregationStrategy<Object> {
    public Object getValue(Exchange exchange) {
        return exchange.getIn().getBody();
    }
}
