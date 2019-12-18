/*
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
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.utils.cassandra.CassandraLoadBalancingPolicies;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;

/**
 * The cql component aims at integrating Cassandra 2.0+ using the CQL3 API (not
 * the Thrift API). It's based on Cassandra Java Driver provided by DataStax.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "cql", title = "Cassandra CQL", syntax = "cql:beanRef:hosts:port/keyspace", label = "database,nosql")
public class CassandraEndpoint extends ScheduledPollEndpoint {

    private volatile CassandraSessionHolder sessionHolder;

    @UriPath(description = "beanRef is defined using bean:id")
    private String beanRef;
    @UriPath
    private String hosts;
    @UriPath
    private Integer port;
    @UriPath
    private String keyspace;
    @UriParam
    private String cql;
    @UriParam(defaultValue = "true")
    private boolean prepareStatements = true;
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
    @UriParam
    private ConsistencyLevel consistencyLevel;
    @UriParam
    private String loadBalancingPolicy;
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

    @Override
    public Producer createProducer() throws Exception {
        return new CassandraProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CassandraConsumer consumer = new CassandraConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // we can get the cluster using various ways

        if (cluster == null && beanRef != null) {
            Object bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanRef);
            if (bean instanceof Session) {
                session = (Session)bean;
                cluster = session.getCluster();
                keyspace = session.getLoggedKeyspace();
            } else if (bean instanceof Cluster) {
                cluster = (Cluster)bean;
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
        CassandraLoadBalancingPolicies cassLoadBalancingPolicies = new CassandraLoadBalancingPolicies();
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
        if (loadBalancingPolicy != null && !loadBalancingPolicy.isEmpty()) {
            clusterBuilder.withLoadBalancingPolicy(cassLoadBalancingPolicies.getLoadBalancingPolicy(loadBalancingPolicy));
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

    public String getBean() {
        return beanRef;
    }

    /**
     * Instead of using a hostname:port, refer to an existing configured Session
     * or Cluster from the Camel registry to be used.
     */
    public void setBean(String beanRef) {
        this.beanRef = beanRef;
    }

    @Deprecated
    public String getBeanRef() {
        return beanRef;
    }

    @Deprecated
    public void setBeanRef(String beanRef) {
        this.beanRef = beanRef;
    }

    public String getHosts() {
        return hosts;
    }

    /**
     * Hostname(s) cassansdra server(s). Multiple hosts can be separated by
     * comma.
     */
    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * Port number of cassansdra server(s)
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getKeyspace() {
        return keyspace;
    }

    /**
     * Keyspace to use
     */
    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public String getCql() {
        return cql;
    }

    /**
     * CQL query to perform. Can be overridden with the message header with key
     * CamelCqlQuery.
     */
    public void setCql(String cql) {
        this.cql = cql;
    }

    public Cluster getCluster() {
        return cluster;
    }

    /**
     * To use the Cluster instance (you would normally not use this option)
     */
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

    /**
     * To use the Session instance (you would normally not use this option)
     */
    public void setSession(Session session) {
        this.session = session;
    }

    public String getClusterName() {
        return clusterName;
    }

    /**
     * Cluster name
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for session authentication
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for session authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    /**
     * Consistency level to use
     */
    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public ResultSetConversionStrategy getResultSetConversionStrategy() {
        return resultSetConversionStrategy;
    }

    /**
     * To use a custom class that implements logic for converting ResultSet into
     * message body ALL, ONE, LIMIT_10, LIMIT_100...
     */
    public void setResultSetConversionStrategy(ResultSetConversionStrategy resultSetConversionStrategy) {
        this.resultSetConversionStrategy = resultSetConversionStrategy;
    }

    public boolean isPrepareStatements() {
        return prepareStatements;
    }

    /**
     * Whether to use PreparedStatements or regular Statements
     */
    public void setPrepareStatements(boolean prepareStatements) {
        this.prepareStatements = prepareStatements;
    }

    /**
     * To use a specific LoadBalancingPolicy
     */
    public String getLoadBalancingPolicy() {
        return loadBalancingPolicy;
    }

    public void setLoadBalancingPolicy(String loadBalancingPolicy) {
        this.loadBalancingPolicy = loadBalancingPolicy;
    }

}
