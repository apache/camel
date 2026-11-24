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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

import static org.apache.camel.utils.cassandra.CassandraUtils.isEmpty;

/**
 * Cassandra 2 CQL3 producer.
 * <dl>
 * <dt>In Message</dt>
 * <dd>Bound parameters: Map of named values (for named markers such as {@code :login}), Collection of Objects, Array of
 * Objects, Simple Object
 * <dd>
 * <dt>Out Message</dt>
 * <dd>List of all Rows
 * <dd>
 * <dl>
 */
public class CassandraProducer extends DefaultProducer {

    private PreparedStatement preparedStatement;

    public CassandraProducer(CassandraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (isPrepareStatements() && getEndpoint().getCql() != null) {
            this.preparedStatement = getEndpoint().prepareStatement();
        }
    }

    @Override
    protected void doStop() throws Exception {
        this.preparedStatement = null;
        super.doStop();
    }

    @Override
    public CassandraEndpoint getEndpoint() {
        return (CassandraEndpoint) super.getEndpoint();
    }

    public boolean isPrepareStatements() {
        return getEndpoint().isPrepareStatements();
    }

    private Object[] getCqlParams(Message message) {
        Object cqlParamsObj = message.getBody();
        Object[] cqlParams;
        final Class<Object[]> objectArrayClazz = Object[].class;
        if (cqlParamsObj == null) {
            cqlParams = null;
        } else if (objectArrayClazz.isInstance(cqlParamsObj)) {
            cqlParams = objectArrayClazz.cast(cqlParamsObj);
        } else if (cqlParamsObj instanceof Collection<?> cqlParamsColl) {
            cqlParams = cqlParamsColl.toArray();
        } else {
            cqlParams = new Object[] { cqlParamsObj };
        }
        return cqlParams;
    }

    /**
     * Execute CQL query using incoming message body has statement parameters.
     */
    private ResultSet execute(Message message) {
        Object messageCql = message.getHeader(CassandraConstants.CQL_QUERY);
        // Convert Empty string to null
        if (messageCql instanceof String && ((String) messageCql).isEmpty()) {
            messageCql = null;
        }
        // a Map body binds the markers by name, any other body by position
        Map<?, ?> namedParams = message.getBody() instanceof Map<?, ?> map ? map : null;
        Object[] cqlParams = namedParams == null ? getCqlParams(message) : null;

        ResultSet resultSet;
        CqlSession session = getEndpoint().getSessionHolder().getSession();
        if (isPrepareStatements()) {
            resultSet = executePreparedStatement(session, messageCql, cqlParams, namedParams);
        } else {
            resultSet = executeStatement(session, messageCql, cqlParams, namedParams);
        }
        return resultSet;
    }

    /**
     * Execute CQL as PreparedStatement
     */
    private ResultSet executePreparedStatement(
            CqlSession session, Object messageCql, Object[] cqlParams, Map<?, ?> namedParams) {
        ResultSet resultSet;
        PreparedStatement lPreparedStatement;
        if (messageCql == null) {
            // URI CQL
            lPreparedStatement = this.preparedStatement;
        } else if (messageCql instanceof String) {
            // Message CQL
            lPreparedStatement = getEndpoint().prepareStatement((String) messageCql);
        } else if (messageCql instanceof SimpleStatement) {
            // Message Statement
            lPreparedStatement = getEndpoint().getSession().prepare((SimpleStatement) messageCql);
        } else {
            throw new IllegalArgumentException("Invalid " + CassandraConstants.CQL_QUERY + " header");
        }
        if (namedParams != null) {
            resultSet = session.execute(lPreparedStatement.bind(namedValues(lPreparedStatement, namedParams)));
        } else if (isEmpty(cqlParams)) {
            resultSet = session.execute(lPreparedStatement.bind());
        } else {
            resultSet = session.execute(lPreparedStatement.bind(cqlParams));
        }
        return resultSet;
    }

    /**
     * Execute CQL as is
     */
    private ResultSet executeStatement(CqlSession session, Object messageCql, Object[] cqlParams, Map<?, ?> namedParams) {
        ResultSet resultSet = null;
        String cql = null;
        SimpleStatement statement = null;
        if (messageCql == null) {
            // URI CQL
            cql = getEndpoint().getCql();
        } else if (messageCql instanceof String) {
            // Message CQL
            cql = (String) messageCql;
        } else if (messageCql instanceof SimpleStatement) {
            // Message Statement
            statement = (SimpleStatement) messageCql;
        } else {
            throw new IllegalArgumentException("Invalid " + CassandraConstants.CQL_QUERY + " header");
        }
        if (statement != null) {
            resultSet = session.execute(statement);
        } else if (namedParams != null) {
            Map<String, Object> values = new LinkedHashMap<>();
            namedParams.forEach((name, value) -> values.put(String.valueOf(name), value));
            resultSet = session.execute(SimpleStatement.newInstance(cql, values));
        } else if (isEmpty(cqlParams)) {
            resultSet = session.execute(cql);
        } else {
            resultSet = session.execute(
                    SimpleStatement.builder(cql).addPositionalValues(cqlParams).build());
        }
        return resultSet;
    }

    /**
     * The values of the bind markers of a prepared statement, in order, taken by name from the Map body.
     */
    private static Object[] namedValues(PreparedStatement statement, Map<?, ?> namedParams) {
        ColumnDefinitions markers = statement.getVariableDefinitions();
        Object[] values = new Object[markers.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = namedValue(namedParams, markers.get(i).getName().asInternal());
        }
        return values;
    }

    private static Object namedValue(Map<?, ?> namedParams, String name) {
        if (namedParams.containsKey(name)) {
            return namedParams.get(name);
        }
        // an unquoted marker such as :firstName is lower case in CQL, so match the keys ignoring case
        for (Map.Entry<?, ?> entry : namedParams.entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException(
                "The Map body has no value for the bind marker :" + name + " of the CQL statement. The keys of the body are: "
                                           + namedParams.keySet());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ResultSet resultSet = execute(exchange.getIn());
        getEndpoint().fillMessage(resultSet, exchange.getMessage());
    }

}
