/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;

/**
 * Cassandra 2 CQL3 endpoint
 */
@UriEndpoint(scheme = "cql", consumerClass = CassandraConsumer.class, label = "database,nosql")
public class CassandraEndpoint extends DefaultEndpoint {

    private volatile CassandraSessionHolder sessionHolder;

    @UriPath
    private String beanRef;
    @UriPath
    private String hosts;
    @UriPath
    private Integer port;
    @UriPath
    private String keyspace;
    @UriParam
    private String cql;
    /**
     * Use PreparedStatements or normal Statements
     */
    @UriParam
    private boolean prepareStatements=true;
    @UriParam
    private String clusterName;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam
    private Cluster cluster;
    @UriParam
    private Session session;

    /**
     * Consistency level: ONE, TWO, QUORUM, LOCAL_QUORUM, ALL...
     */
    @UriParam
    private ConsistencyLevel consistencyLevel;

    /**
     * How many rows should be retrieved in message body
     */
    @UriParam
    private ResultSetConversionStrategy resultSetConversionStrategy = ResultSetConversionStrategies.all();

    public CassandraEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public CassandraEndpoint(String uri, CassandraComponent component, Cluster cluster, Session session, String keyspace) {
        super(uri, component);
        this.cluster = cluster;
        this.session = session;
        this.keyspace = keyspace;
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // we can get the cluster using various ways

        if (cluster == null && beanRef != null) {
            Object bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanRef);
            if (bean instanceof Session) {
                session = (Session) bean;
                cluster = session.getCluster();
                keyspace = session.getLoggedKeyspace();
            } else if (bean instanceof Cluster) {
                cluster = (Cluster) bean;
                session = null;
            } else {
                throw new IllegalArgumentException("CQL Bean type should be of type Session or Cluster but was " + bean);
            }
        }

        if (cluster == null && hosts != null) {
            // use the cluster builder to create the cluster
            cluster = createClusterBuilder().build();
        }

        if (cluster != null) {
            sessionHolder = new CassandraSessionHolder(cluster, keyspace);
        } else {
            sessionHolder = new CassandraSessionHolder(session);
        }

        sessionHolder.start();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        sessionHolder.stop();
    }

    protected CassandraSessionHolder getSessionHolder() {
        return sessionHolder;
    }

    protected Cluster.Builder createClusterBuilder() throws Exception {
        Cluster.Builder clusterBuilder = Cluster.builder();
        for (String host : hosts.split(",")) {
            clusterBuilder = clusterBuilder.addContactPoint(host);
        }
        if (port != null) {
            clusterBuilder = clusterBuilder.withPort(port);
        }
        if (clusterName != null) {
            clusterBuilder = clusterBuilder.withClusterName(clusterName);
        }
        if (username != null && !username.isEmpty() && password != null) {
            clusterBuilder.withCredentials(username, password);
        }
        return clusterBuilder;
    }

    /**
     * Create and configure a Prepared CQL statement
     */
    protected PreparedStatement prepareStatement(String cql) {
        PreparedStatement preparedStatement = getSessionHolder().getSession().prepare(cql);
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

    public String getBeanRef() {
        return beanRef;
    }

    public void setBeanRef(String beanRef) {
        this.beanRef = beanRef;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public String getCql() {
        return cql;
    }

    public void setCql(String cql) {
        this.cql = cql;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public Session getSession() {
        if (session == null) {
            return sessionHolder.getSession();
        } else {
            return session;
        }
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public boolean isPrepareStatements() {
        return prepareStatements;
    }

    public void setPrepareStatements(boolean prepareStatements) {
        this.prepareStatements = prepareStatements;
    }
}
