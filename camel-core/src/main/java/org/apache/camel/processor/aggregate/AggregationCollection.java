package org.apache.camel.processor.aggregate;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * A {@link Collection} which aggregates exchanges together,
 * using a correlation {@link Expression} and a {@link AggregationStrategy}.
 * <p/>
 * The Default Implementation will group messages based on the correlation expression.
 * Other implementations could for instance just add all exchanges as a batch.
 *
 * @version $Revision$
 */
public interface AggregationCollection extends Collection<Exchange> {

    /**
     * Gets the correlation expression
     */
    Expression<Exchange> getCorrelationExpression();

    /**
     * Sets the correlation expression to be used
     */
    void setCorrelationExpression(Expression<Exchange> correlationExpression);

    /**
     * Gets the aggregation strategy
     */
    AggregationStrategy getAggregationStrategy();

    /**
     * Sets the aggregation strategy to be used
     */
    void setAggregationStrategy(AggregationStrategy aggregationStrategy);

    /**
     * Adds the given exchange to this collection
     */
    boolean add(Exchange exchange);

    /**
     * Gets the iterator to iterate this collection.
     */
    Iterator<Exchange> iterator();

    /**
     * Gets the size of this collection
     */
    int size();

    /**
     * Clears this colleciton
     */
    void clear();

}
