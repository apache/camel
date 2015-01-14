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
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * Cassandra 2 CQL3 consumer.
 */
public class CassandraConsumer extends ScheduledPollConsumer {

    /**
     * Prepared statement used for polling
     */
    private PreparedStatement preparedStatement;

    public CassandraConsumer(CassandraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public CassandraEndpoint getEndpoint() {
        return (CassandraEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        // Execute CQL Query
        Session session = getEndpoint().getSessionHolder().getSession();
        ResultSet resultSet;
        if (isPrepareStatements()) {
            resultSet = session.execute(preparedStatement.bind());
        } else {
            resultSet = session.execute(getEndpoint().getCql());
        }

        // Create message from ResultSet
        Exchange exchange = getEndpoint().createExchange();
        Message message = exchange.getIn();
        getEndpoint().fillMessage(resultSet, message);

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (isPrepareStatements()) {
            preparedStatement = getEndpoint().prepareStatement();
        }
    }

    @Override
    protected void doStop() throws Exception {
        this.preparedStatement = null;
        super.doStop();
    }

    public boolean isPrepareStatements() {
        return getEndpoint().isPrepareStatements();
    }

}
