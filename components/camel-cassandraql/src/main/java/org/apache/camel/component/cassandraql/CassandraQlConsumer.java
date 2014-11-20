package org.apache.camel.component.cassandraql;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * Cassandra 2 CQL3 consumer.
 */
public class CassandraQlConsumer extends ScheduledPollConsumer {

    /**
     * Prepared statement used for polling
     */
    private PreparedStatement preparedStatement;

    public CassandraQlConsumer(CassandraQlEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public CassandraQlEndpoint getEndpoint() {
        return (CassandraQlEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        // Execute CQL Query
        Session session = getEndpoint().getSession();
        if (preparedStatement == null) {
            preparedStatement = getEndpoint().prepareStatement();
        }
        ResultSet resultSet = session.execute(preparedStatement.bind());
        
        // Create message from ResultSet
        Exchange exchange = getEndpoint().createExchange();
        Message message = exchange.getIn();
        getEndpoint().fillMessage(resultSet, message);

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
