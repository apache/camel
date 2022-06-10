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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;

import static org.apache.camel.component.cassandra.CassandraConstants.CASSANDRA_RESUME_ACTION;

/**
 * Cassandra 2 CQL3 consumer.
 */
public class CassandraConsumer extends ScheduledPollConsumer implements ResumeAware<ResumeStrategy> {

    /**
     * Prepared statement used for polling
     */
    private PreparedStatement preparedStatement;

    private ResumeStrategy resumeStrategy;

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
        CqlSession session = getEndpoint().getSessionHolder().getSession();
        ResultSet resultSet;
        if (isPrepareStatements()) {
            resultSet = session.execute(preparedStatement.bind());
        } else {
            resultSet = session.execute(getEndpoint().getCql());
        }

        // Create message from ResultSet
        Exchange exchange = createExchange(false);

        try {
            Message message = exchange.getIn();
            getEndpoint().fillMessage(resultSet, message);
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
            releaseExchange(exchange, false);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (isPrepareStatements()) {
            preparedStatement = getEndpoint().prepareStatement();
        }

        ResumeStrategyHelper.resume(getEndpoint().getCamelContext(), this, resumeStrategy, CASSANDRA_RESUME_ACTION);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        this.preparedStatement = null;
        super.doStop();
    }

    public boolean isPrepareStatements() {
        return getEndpoint().isPrepareStatements();
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }
}
