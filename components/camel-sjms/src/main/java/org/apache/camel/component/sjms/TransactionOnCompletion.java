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
package org.apache.camel.component.sjms;

import java.util.Objects;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.support.SynchronizationAdapter;

import static org.apache.camel.component.sjms.SjmsHelper.*;

/**
 * Completion {@link org.apache.camel.spi.Synchronization} work when processing the message is complete to either commit
 * or rollback the session.
 */
class TransactionOnCompletion extends SynchronizationAdapter {

    // TODO: close session, connection

    private final Session session;
    private final Message message;

    public TransactionOnCompletion(Session session, Message message) {
        this.session = session;
        this.message = message;
    }

    @Override
    public void onDone(Exchange exchange) {
        try {
            if (exchange.isFailed() || exchange.isRollbackOnly()) {
                rollbackIfNeeded(session);
            } else {
                commitIfNeeded(session, message);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            closeSession(session);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TransactionOnCompletion)) {
            return false;
        }

        TransactionOnCompletion that = (TransactionOnCompletion) o;
        return session == that.session && message == that.message;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), session, message);
    }
}
