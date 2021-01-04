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
package org.apache.camel.component.sjms.tx;

import javax.jms.Connection;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.TransactionCommitStrategy;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SessionTransactionSynchronization is called at the completion of each {@link org.apache.camel.Exchange}.
 * <p/>
 * The commit or rollback on the {@link Session} must be performed from the same thread that consumed the message.
 */
public class SessionTransactionSynchronization extends SynchronizationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SessionTransactionSynchronization.class);

    private final Connection connection;
    private final Session session;
    private final TransactionCommitStrategy commitStrategy;
    private final boolean close;

    public SessionTransactionSynchronization(Connection connection, Session session, TransactionCommitStrategy commitStrategy,
                                             boolean close) {
        this.connection = connection;
        this.session = session;
        this.close = close;
        if (commitStrategy == null) {
            this.commitStrategy = new DefaultTransactionCommitStrategy();
        } else {
            this.commitStrategy = commitStrategy;
        }
    }

    @Override
    public void onFailure(Exchange exchange) {
        try {
            if (commitStrategy.rollback(exchange)) {
                LOG.debug("Processing failure of ExchangeId: {}", exchange.getExchangeId());
                if (session != null && session.getTransacted()) {
                    session.rollback();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to rollback the JMS session: {}", e.getMessage());
        }

        if (close) {
            close(session);
            close(connection);
        }
    }

    @Override
    public void onComplete(Exchange exchange) {
        try {
            if (commitStrategy.commit(exchange)) {
                LOG.debug("Processing completion of ExchangeId: {}", exchange.getExchangeId());
                if (session != null && session.getTransacted()) {
                    session.commit();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to commit the JMS session: {}", e.getMessage());
            exchange.setException(e);
        }

        if (close) {
            close(session);
            close(connection);
        }
    }

    @Override
    public boolean allowHandover() {
        // must not handover as we should be synchronous
        return false;
    }

    private static void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    private static void close(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

}
