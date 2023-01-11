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

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

public final class SjmsHelper {

    private SjmsHelper() {
    }

    public static void closeProducer(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void closeConnection(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void closeSession(Session ses) {
        if (ses != null) {
            try {
                ses.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void closeConsumer(MessageConsumer consumer) {
        if (consumer != null) {
            boolean interrupted = Thread.interrupted();
            try {
                consumer.close();
            } catch (JMSException ex) {
                // ignore
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static void commitIfNecessary(Session session) throws JMSException {
        try {
            session.commit();
        } catch (jakarta.jms.TransactionInProgressException | jakarta.jms.IllegalStateException ex) {
            // ignore
        }
    }

    public static void commitIfNeeded(Session session, Message message) throws Exception {
        try {
            if (session.getTransacted()) {
                SjmsHelper.commitIfNecessary(session);
            } else if (message != null && session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE) {
                message.acknowledge();
            }
        } catch (jakarta.jms.TransactionInProgressException | jakarta.jms.IllegalStateException ex) {
            // ignore
        }
    }

    public static void rollbackIfNeeded(Session session) throws JMSException {
        if (session.getTransacted()) {
            try {
                session.rollback();
            } catch (jakarta.jms.TransactionInProgressException | jakarta.jms.IllegalStateException ex) {
                // ignore
            }
        } else if (session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE) {
            try {
                session.recover();
            } catch (jakarta.jms.IllegalStateException ex) {
                // ignore
            }
        }
    }

    public static boolean isTransactionOrClientAcknowledgeMode(Session session) throws JMSException {
        return session.getTransacted() || session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE;
    }

}
