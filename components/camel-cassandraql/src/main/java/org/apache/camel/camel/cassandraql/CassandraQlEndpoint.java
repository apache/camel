package org.apache.camel.camel.cassandraql;

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

/**
 * Cassandra 2 CQL3 endpoint
 */
public class CassandraQlEndpoint extends DefaultEndpoint {

    /**
     * Cluster
     */
    private final Cluster cluster;
    /**
     * Session
     */
    private Session session;
    /**
     * Keyspace name
     */
    private String keyspace;
    /**
     * Indicates whether Session is externally managed
     */
    private final boolean managedSession;
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
     * Execute queries asynchronously
     */
    private boolean async = false;

    /**
     * Cassandra URI
     *
     * @param uri
     * @param component Parent component
     * @param cluster Cluster (required)
     * @param session Session (optional)
     * @param keyspace Keyspace (optional)
     */
    public CassandraQlEndpoint(String uri, CassandraQlComponent component, Cluster cluster, Session session, String keyspace) {
        super(uri, component);
        this.cluster = cluster;
        this.session = session;
        this.managedSession = session != null;
        this.keyspace = keyspace;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!managedSession && session == null) {
            if (keyspace == null) {
                this.session = cluster.connect();
            } else {
                this.session = cluster.connect(keyspace);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (managedSession && session != null) {
            session.close();
        }
        super.doStop();
    }

    public Producer createProducer() throws Exception {
        return new CassandraQlProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CassandraQlConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public Session getSession() {
        return session;
    }

    public String getCql() {
        return cql;
    }

    public void setCql(String cql) {
        this.cql = cql;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
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
        message.setBody(resultSet.all());
    }

}
