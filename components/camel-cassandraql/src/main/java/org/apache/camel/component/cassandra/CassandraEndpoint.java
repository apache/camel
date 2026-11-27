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
import java.util.Map;

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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.utils.cassandra.CassandraExtraCodecs;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query and update an Apache Cassandra database with CQL statements.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "cql", title = "Cassandra CQL", syntax = "cql:beanRef:hosts:port/keyspace",
             category = { Category.DATABASE, Category.BIGDATA }, headersClass = CassandraConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.LIST, dependsOn = "resultSetConversionStrategy",
                                   byOption = @BodyType.When(option = "resultSetConversionStrategy", value = "ONE",
                                                             carriers = { Carrier.POJO, Carrier.NULL },
                                                             javaType = "com.datastax.oss.driver.api.core.cql.Row"),
                                   description = "One message per poll with the rows of the cql query: a List of the Row of"
                                                 + " the Cassandra driver, empty when there is none. ONE gives the first Row"
                                                 + " or null, LIMIT_n a List of at most n rows"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The values of the ? placeholders of the CQL (the cql option or the"
                                                + " CamelCqlQuery header): an array or a Collection of them, or one value for"
                                                + " one placeholder. No body, an empty array or an empty Collection binds"
                                                + " none; any other body is one value, so a body left from an earlier step"
                                                + " fails a CQL without placeholders"),
           producerOut = @BodyType(value = Carrier.LIST, dependsOn = "resultSetConversionStrategy",
                                   byOption = @BodyType.When(option = "resultSetConversionStrategy", value = "ONE",
                                                             carriers = { Carrier.POJO, Carrier.NULL },
                                                             javaType = "com.datastax.oss.driver.api.core.cql.Row"),
                                   description = "The rows: a List of the Row of the Cassandra driver, empty for an insert,"
                                                 + " update or delete. ONE gives the first Row or null, LIMIT_n a List of at"
                                                 + " most n rows"))
public class CassandraEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraEndpoint.class);

    private volatile CassandraSessionHolder sessionHolder;
    // whether the endpoint created the session from its hosts (and so closes it when it stops)
    private boolean createdSession;

    @UriPath(description = "The name of a com.datastax.oss.driver.api.core.CqlSession in the registry, written as bean:name"
                           + " in the URI. The endpoint uses the keyspace of this session, not the keyspace in the URI, and"
                           + " does not close the session. The session option replaces it.")
    private String beanRef;
    @UriPath(description = "The host names or IP addresses of the Cassandra nodes that the driver contacts first, separated"
                           + " by commas. When neither the session option nor a bean is set, the endpoint creates a session"
                           + " from them and closes it when it stops.")
    private String hosts;
    @UriPath(description = "The port of the CQL native protocol, the same for all the nodes in the hosts. If not set, 9042 is"
                           + " used.")
    private Integer port;
    @UriPath(description = "The keyspace of the session that the endpoint creates from the hosts, so that the CQL can name"
                           + " tables without their keyspace. It is not used with the session option or a bean.")
    private String keyspace;
    @UriParam(defaultValue = "datacenter1",
              description = "The name of the local datacenter, whose nodes the default load balancing policy of the driver"
                            + " sends the requests to. Used only for the session that the endpoint creates from the hosts.")
    private String datacenter = "datacenter1";
    @UriParam(description = "The CQL statement that the consumer executes at each poll, and that the producer executes when"
                            + " the CamelCqlQuery header is not set. The producer binds the values of the ? or :name markers"
                            + " from the message body.")
    private String cql;
    @UriParam(defaultValue = "true",
              description = "Whether to execute the CQL as a prepared statement. The cql option is prepared once when the"
                            + " consumer or producer starts, and a CQL from the CamelCqlQuery header is prepared for each"
                            + " message. If false, the CQL is sent as a simple statement each time.")
    private boolean prepareStatements = true;
    @UriParam(description = "Not used: the component does not pass this value to the driver.")
    private String clusterName;
    @UriParam(label = "security", security = "secret",
              description = "The user name for plain text authentication with the Cassandra nodes. Used only together with"
                            + " the password option, and only for the session that the endpoint creates from the hosts.")
    private String username;
    @UriParam(label = "security", security = "secret",
              description = "The password for plain text authentication with the Cassandra nodes. Used only together with"
                            + " the username option, and only for the session that the endpoint creates from the hosts.")
    private String password;
    @UriParam(label = "advanced",
              description = "To use a custom com.datastax.oss.driver.api.core.CqlSession. When set, the endpoint does not"
                            + " create a session from the hosts or look up a bean, and does not close this session. The"
                            + " options for creating a session, such as keyspace, datacenter, username and password, are not"
                            + " used.")
    private CqlSession session;
    private DefaultConsistencyLevel consistencyLevel;
    @UriParam(label = "advanced",
              description = "The class of the load balancing policy of the driver (its basic.load-balancing-policy.class"
                            + " setting). A class name without a package, such as DcInferringLoadBalancingPolicy, is one of"
                            + " the policies of the driver; if not set, the driver uses DefaultLoadBalancingPolicy. Used only"
                            + " for the session that the endpoint creates from the hosts.")
    private String loadBalancingPolicyClass;
    @UriParam(label = "advanced",
              description = "How the rows of the result become the message body: ALL for a List of all the"
                            + " com.datastax.oss.driver.api.core.cql.Row objects (the default), ONE for the first Row or"
                            + " null, LIMIT_n, such as LIMIT_10, for a List of at most n rows. A custom"
                            + " org.apache.camel.component.cassandra.ResultSetConversionStrategy can be set instead.")
    private ResultSetConversionStrategy resultSetConversionStrategy = ResultSetConversionStrategies.all();
    @UriParam(label = "advanced",
              description = "The extra codecs of the driver (from its ExtraTypeCodecs class) to register, separated by"
                            + " commas: BLOB_TO_ARRAY, BOOLEAN_LIST_TO_ARRAY, BYTE_LIST_TO_ARRAY, SHORT_LIST_TO_ARRAY,"
                            + " INT_LIST_TO_ARRAY, LONG_LIST_TO_ARRAY, FLOAT_LIST_TO_ARRAY, DOUBLE_LIST_TO_ARRAY,"
                            + " TIMESTAMP_UTC, TIMESTAMP_MILLIS_SYSTEM, TIMESTAMP_MILLIS_UTC, ZONED_TIMESTAMP_SYSTEM,"
                            + " ZONED_TIMESTAMP_UTC, ZONED_TIMESTAMP_PERSISTED, LOCAL_TIMESTAMP_SYSTEM or"
                            + " LOCAL_TIMESTAMP_UTC. An unknown name is ignored. Used only for the session that the endpoint"
                            + " creates from the hosts.")
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
    public String getServiceUrl() {
        if (hosts != null && port != null) {
            return hosts + ":" + port;
        } else if (hosts != null) {
            return hosts;
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "cql";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (username != null) {
            return Map.of("username", username);
        }
        return null;
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

        createdSession = false;
        if (session == null && hosts != null) {
            // use the session builder to create the cluster
            session = createSessionBuilder().build();
            createdSession = true;
        }

        // the endpoint closes only the session it created from its hosts; a session it was given (session) or looked
        // up (beanRef) is managed outside, and other endpoints, repositories or the application can use it
        sessionHolder = new CassandraSessionHolder(session, !createdSession);

        sessionHolder.start();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (sessionHolder != null) {
            sessionHolder.stop();
        }
        if (createdSession) {
            // the session is closed, so the next start creates a new one from the hosts
            session = null;
            createdSession = false;
        }
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
                codec = codec.trim();
                CassandraExtraCodecs ec = getCamelContext().getTypeConverter().tryConvertTo(CassandraExtraCodecs.class, codec);
                if (ec != null) {
                    sessionBuilder.addTypeCodecs(ec.codec());
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

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getCql() {
        return cql;
    }

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

    public void setSession(CqlSession session) {
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

    /**
     * Consistency level to use
     */
    public void setConsistencyLevel(DefaultConsistencyLevel consistencyLevel) {
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

    public String getLoadBalancingPolicyClass() {
        return loadBalancingPolicyClass;
    }

    public void setLoadBalancingPolicyClass(String loadBalancingPolicyClass) {
        this.loadBalancingPolicyClass = loadBalancingPolicyClass;
    }

    public String getExtraTypeCodecs() {
        return extraTypeCodecs;
    }

    public void setExtraTypeCodecs(String extraTypeCodecs) {
        this.extraTypeCodecs = extraTypeCodecs;
    }
}
