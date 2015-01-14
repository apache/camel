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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Cassandra 2 CQL3 producer.
 * <dl>
 * <dt>In Message</dt>
 * <dd>Bound parameters: Collection of Objects, Array of Objects, Simple Object<dd>
 * <dt>Out Message</dt>
 * <dd>List of all Rows<dd>
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
        Object cqlParamsObj = message.getBody();
        Object[] cqlParams;
        final Class<Object[]> objectArrayClazz = Object[].class;
        if (cqlParamsObj == null) {
            cqlParams = null;
        } else if (objectArrayClazz.isInstance(cqlParamsObj)) {
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
        Session session = getEndpoint().getSessionHolder().getSession();
        if (cqlParams == null || cqlParams.length==0) {
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
