package org.apache.camel.component.pgevent;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PgEvent consumer.
 */
public class PgEventConsumer extends DefaultConsumer implements PGNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(PgEventConsumer.class);
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;

    public PgEventConsumer(PgEventEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        
        dbConnection = endpoint.initJdbc();
        dbConnection.createStatement().execute("LISTEN "+endpoint.getChannel());
        dbConnection.addNotificationListener(endpoint.getChannel(), endpoint.getChannel(), this);
    }

    @Override
    public void notification(int processId, String channel, String payload) {
        Exchange outOnly = endpoint.createExchange();
        Message msg = outOnly.getOut();
        msg.setHeader("channel", channel);
        msg.setBody(payload);
        outOnly.setOut(msg);
        try {
            getProcessor().process(outOnly);
        } catch (Exception ex) {
            LOG.error("Unable to process incoming notification from PostgreSQL: processId='"+processId+"', channel='"+channel+"', payload='"+payload+"'", ex);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        dbConnection.removeNotificationListener(endpoint.getChannel());
        dbConnection.close();
    }
}
