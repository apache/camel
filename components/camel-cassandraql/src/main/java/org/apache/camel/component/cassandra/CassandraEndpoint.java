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

import java.net.InetSocketAddress;
import java.util.Arrays;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.apache.camel.Category;
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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.utils.cassandra.CassandraExtraCodecs;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integrate with Cassandra 2.0+ using the CQL3 API (not the Thrift API). Based on Cassandra Java Driver provided by
 * DataStax.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "cql", title = "Cassandra CQL", syntax = "cql:beanRef:hosts:port/keyspace",
             category = { Category.DATABASE, Category.BIGDATA }, headersClass = CassandraConstants.class)
public class CassandraEndpoint extends ScheduledPollEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraEndpoint.class);

    private volatile CassandraSessionHolder sessionHolder;

    @UriPath(description = "beanRef is defined using bean:id")
    private String beanRef;
    @UriPath
    private String hosts;
    @UriPath
    private Integer port;
    @UriPath
    private String keyspace;
    @UriParam(defaultValue = "datacenter1")
    private String datacenter = "datacenter1";
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
    private CqlSession session;
    @UriParam
    private DefaultConsistencyLevel consistencyLevel;
    @UriParam
    private String loadBalancingPolicyClass;
    @UriParam
    private ResultSetConversionStrategy resultSetConversionStrategy = ResultSetConversionStrategies.all();
    @UriParam
    private String extraTypeCodecs;

    public CassandraEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public CassandraEndpoint(String uri, CassandraComponent component, CqlSession session, String keyspace) {
        super(uri, component);
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
        if (session == null && beanRef != null) {
            Object bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanRef);
            if (bean instanceof CqlSession) {
                session = (CqlSession) bean;
                keyspace = session.getKeyspace().isPresent() ? session.getKeyspace().get().toString() : null;
            } else {
                throw new IllegalArgumentException("CQL Bean type should be of type CqlSession but was " + bean);
            }
        }

        if (session == null && hosts != null) {
            // use the session builder to create the cluster
            session = createSessionBuilder().build();
        }

        sessionHolder = new CassandraSessionHolder(session);

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

    protected CqlSessionBuilder createSessionBuilder() {
        CqlSessionBuilder sessionBuilder = CqlSession.builder();
        for (String host : hosts.split(",")) {
            sessionBuilder.addContactPoint(new InetSocketAddress(host, port == null ? 9042 : port));
        }
        if (username != null && !username.isEmpty() && password != null) {
            sessionBuilder.withAuthCredentials(username, password);
        }
        if (loadBalancingPolicyClass != null && !loadBalancingPolicyClass.isEmpty()) {
            DriverConfigLoader driverConfigLoader = DriverConfigLoader.programmaticBuilder()
                    .withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, loadBalancingPolicyClass)
                    .build();
            sessionBuilder.withConfigLoader(driverConfigLoader);
        }

        sessionBuilder.withLocalDatacenter(datacenter);
        sessionBuilder.withKeyspace(keyspace);

        ClassLoader classLoader = getCamelContext().getApplicationContextClassLoader();
        if (classLoader != null) {
            sessionBuilder.withClassLoader(classLoader);
        }

        if (extraTypeCodecs != null) {
            String[] c = extraTypeCodecs.split(",");

            if (LOG.isDebugEnabled()) {
                LOG.debug(Arrays.toString(c));
            }

            for (String codec : c) {
                if (ObjectHelper.isNotEmpty(CassandraExtraCodecs.valueOf(codec))) {
                    sessionBuilder.addTypeCodecs(CassandraExtraCodecs.valueOf(codec).codec());
                }
            }
        }

        return sessionBuilder;
    }

    /**
     * Create and configure a Prepared CQL statement
     */
    protected PreparedStatement prepareStatement(String cql) {
        SimpleStatement statement = SimpleStatement.builder(cql)
                .setConsistencyLevel(consistencyLevel).build();
        return getSessionHolder().getSession().prepare(statement);
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
     * Instead of using a hostname:port, refer to an existing configured Session or Cluster from the Camel registry to
     * be used.
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
     * Hostname(s) Cassandra server(s). Multiple hosts can be separated by comma.
     */
    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * Port number of Cassandra server(s)
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

    public String getDatacenter() {
        return datacenter;
    }

    /**
     * Datacenter to use
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getCql() {
        return cql;
    }

    /**
     * CQL query to perform. Can be overridden with the message header with key CamelCqlQuery.
     */
    public void setCql(String cql) {
        this.cql = cql;
    }

    public CqlSession getSession() {
        if (session == null) {
            return sessionHolder.getSession();
        } else {
            return session;
        }
    }

    /**
     * To use the Session instance (you would normally not use this option)
     */
    public void setSession(CqlSession session) {
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
    public void setConsistencyLevel(DefaultConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public ResultSetConversionStrategy getResultSetConversionStrategy() {
        return resultSetConversionStrategy;
    }

    /**
     * To use a custom class that implements logic for converting ResultSet into message body ALL, ONE, LIMIT_10,
     * LIMIT_100...
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
     * To use a specific LoadBalancingPolicyClass
     */
    public String getLoadBalancingPolicyClass() {
        return loadBalancingPolicyClass;
    }

    public void setLoadBalancingPolicyClass(String loadBalancingPolicyClass) {
        this.loadBalancingPolicyClass = loadBalancingPolicyClass;
    }

    /**
     * To use a specific comma separated list of Extra Type codecs. Possible values are: BLOB_TO_ARRAY,
     * BOOLEAN_LIST_TO_ARRAY, BYTE_LIST_TO_ARRAY, SHORT_LIST_TO_ARRAY, INT_LIST_TO_ARRAY, LONG_LIST_TO_ARRAY,
     * FLOAT_LIST_TO_ARRAY, DOUBLE_LIST_TO_ARRAY, TIMESTAMP_UTC, TIMESTAMP_MILLIS_SYSTEM, TIMESTAMP_MILLIS_UTC,
     * ZONED_TIMESTAMP_SYSTEM, ZONED_TIMESTAMP_UTC, ZONED_TIMESTAMP_PERSISTED, LOCAL_TIMESTAMP_SYSTEM and
     * LOCAL_TIMESTAMP_UTC
     */
    public String getExtraTypeCodecs() {
        return extraTypeCodecs;
    }

    public void setExtraTypeCodecs(String extraTypeCodecs) {
        this.extraTypeCodecs = extraTypeCodecs;
    }
}
