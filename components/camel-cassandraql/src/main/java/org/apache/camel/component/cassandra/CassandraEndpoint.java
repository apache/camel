package org.apache.camel.component.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.apache.camel.Consumer;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;

/**
 * Cassandra 2 CQL3 endpoint
 */
public class CassandraEndpoint extends DefaultEndpoint {
    /**
     * Session holder
     */
    private CassandraSessionHolder sessionHolder;
    /**
     * CQL query
     */
    private String cql;
    /**
     * Consistency level: ONE, TWO, QUORUM, LOCAL_QUORUM, ALL...
     */
    @UriParam
    private ConsistencyLevel consistencyLevel;
    /**
     * How many rows should be retrieved in message body
     */
    private ResultSetConversionStrategy resultSetConversionStrategy = ResultSetConversionStrategies.all();
    /**
     * Cassandra URI
     *
     * @param uri
     * @param component Parent component
     * @param cluster Cluster (required)
     * @param session Session (optional)
     * @param keyspace Keyspace (optional)
     */
    public CassandraEndpoint(String uri, CassandraComponent component, Cluster cluster, Session session, String keyspace) {
        super(uri, component);
        if (session == null) {
            sessionHolder = new CassandraSessionHolder(cluster, keyspace);
        } else {
            sessionHolder = new CassandraSessionHolder(session);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        sessionHolder.start();
    }

    @Override
    protected void doStop() throws Exception {
        sessionHolder.stop();
        super.doStop();
    }

    public Producer createProducer() throws Exception {
        return new CassandraProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CassandraConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public Session getSession() {
        return sessionHolder.getSession();
    }

    public String getCql() {
        return cql;
    }

    public void setCql(String cql) {
        this.cql = cql;
    }

    public String getKeyspace() {
        return sessionHolder.getKeyspace();
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public ResultSetConversionStrategy getResultSetConversionStrategy() {
        return resultSetConversionStrategy;
    }

    public void setResultSetConversionStrategy(ResultSetConversionStrategy resultSetConversionStrategy) {
        this.resultSetConversionStrategy = resultSetConversionStrategy;
    }

    public void setResultSetConversionStrategy(String converter) {
        this.resultSetConversionStrategy = ResultSetConversionStrategies.fromName(converter);
    }

    /**
     * Create and configure a Prepared CQL statement
     */
    protected PreparedStatement prepareStatement(String cql) {
        PreparedStatement preparedStatement = getSession().prepare(cql);
        if (consistencyLevel != null) {
            preparedStatement.setConsistencyLevel(consistencyLevel);
        }
        return preparedStatement;
    }
    /**
     * Create and configure a Prepared CQL statement
     */
    protected PreparedStatement prepareStatement() {
        return prepareStatement(cql);
    }
    /**
     * Copy ResultSet into Message.
     */
    protected void fillMessage(ResultSet resultSet, Message message) {
        message.setBody(resultSetConversionStrategy.getBody(resultSet));
    }

}
