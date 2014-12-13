package org.apache.camel.component.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import java.util.Collection;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cassandra 2 CQL3 producer.
 * <dl>
 *  <dt>In Message</dt>
 *  <dd>Bound parameters: Collection of Objects, Array of Objects, Simple Object<dd>
 *  <dt>Out Message</dt>
 *  <dd>List of all Rows<dd>
 * <dl>
 */
public class CassandraProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CassandraProducer.class);
    private PreparedStatement preparedStatement;
    public CassandraProducer(CassandraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public CassandraEndpoint getEndpoint() {
        return (CassandraEndpoint) super.getEndpoint();
    }

    private Object[] getCqlParams(Message message) {
        Object cqlParamsObj = message.getBody(Object.class);
        Object[] cqlParams;
        final Class<Object[]> objectArrayClazz = Object[].class;
        if (objectArrayClazz.isInstance(cqlParamsObj)) {
            cqlParams = objectArrayClazz.cast(cqlParamsObj);
        } else if (cqlParamsObj instanceof Collection) {
            final Collection cqlParamsColl = (Collection) cqlParamsObj;
            cqlParams = cqlParamsColl.toArray();
        } else {
            cqlParams = new Object[]{cqlParamsObj};
        }
        return cqlParams;
    }
    /**
     * Execute CQL query using incoming message body has statement parameters.
     */
    private ResultSet execute(Message message) {
        String messageCql = message.getHeader(CassandraConstants.CQL_QUERY, String.class);
        Object[] cqlParams = getCqlParams(message);
        
        ResultSet resultSet;  
        PreparedStatement lPreparedStatement;
        if (messageCql == null || messageCql.isEmpty()) {
            // URI CQL
            if (preparedStatement == null) {
                this.preparedStatement = getEndpoint().prepareStatement();
            }
            lPreparedStatement = this.preparedStatement;
        } else {
            // Message CQL
            lPreparedStatement = getEndpoint().prepareStatement(messageCql);
        }
        Session session = getEndpoint().getSession();
        if (cqlParams == null) {
            resultSet = session.execute(lPreparedStatement.bind());
        } else {
            resultSet = session.execute(lPreparedStatement.bind(cqlParams));
        }            
        return resultSet;
    }

    public void process(Exchange exchange) throws Exception {
        ResultSet resultSet = execute(exchange.getIn());
        getEndpoint().fillMessage(resultSet, exchange.getOut());
    }

}
