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
package org.apache.camel.component.sjms.jms;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;


/**
 * TODO Add Class documentation for SessionPool
 * 
 */
public class SessionPool extends ObjectPool<Session> {

    private ConnectionResource connectionResource;
    private boolean transacted;
    private SessionAcknowledgementType acknowledgeMode = SessionAcknowledgementType.AUTO_ACKNOWLEDGE;

    /**
     * TODO Add Constructor Javadoc
     *
     */
    public SessionPool(int poolSize, ConnectionResource connectionResource) {
        super(poolSize);
        this.connectionResource = connectionResource;
    }

    /**
     * TODO Add Constructor Javadoc
     *
     * @param poolSize
     */
    public SessionPool(int poolSize) {
        super(poolSize);
    }
    
    @Override
    protected Session createObject() throws Exception {
        Session session = null;
        final Connection connection = getConnectionResource().borrowConnection(5000);
        if (connection != null) {
            if (transacted) {
                session = connection.createSession(transacted, Session.AUTO_ACKNOWLEDGE);
            } else {
                switch (acknowledgeMode) {
                case CLIENT_ACKNOWLEDGE:
                    session = connection.createSession(transacted, Session.CLIENT_ACKNOWLEDGE);
                    break;
                case DUPS_OK_ACKNOWLEDGE:
                    session = connection.createSession(transacted, Session.DUPS_OK_ACKNOWLEDGE);
                    break;
                case AUTO_ACKNOWLEDGE:
                    session = connection.createSession(transacted, Session.AUTO_ACKNOWLEDGE);
                    break;
                default:
                    // do nothing here.
                }
            }
        }
        getConnectionResource().returnConnection(connection);
        return session;
    }
    
    @Override
    protected void destroyObject(Session session) throws Exception {
     // lets reset the session
        session.setMessageListener(null);

        if (transacted) {
            try {
                session.rollback();
            } catch (JMSException e) {
                logger.warn("Caught exception trying rollback() when putting session back into the pool, will invalidate. " + e, e);
            }
        }
        if (session != null) {
            session.close();
            session = null;
        }
    }

    /**
     * Gets the SessionAcknowledgementType value of acknowledgeMode for this instance of SessionPool.
     *
     * @return the DEFAULT_ACKNOWLEDGE_MODE
     */
    public final SessionAcknowledgementType getAcknowledgeMode() {
        return acknowledgeMode;
    }

    /**
     * Sets the SessionAcknowledgementType value of acknowledgeMode for this instance of SessionPool.
     *
     * @param acknowledgeMode Sets SessionAcknowledgementType, default is AUTO_ACKNOWLEDGE
     */
    public final void setAcknowledgeMode(SessionAcknowledgementType acknowledgeMode) {
        this.acknowledgeMode = acknowledgeMode;
    }

    /**
     * Gets the boolean value of transacted for this instance of SessionPool.
     *
     * @return the transacted
     */
    public final boolean isTransacted() {
        return transacted;
    }

    /**
     * Sets the boolean value of transacted for this instance of SessionPool.
     *
     * @param transacted Sets boolean, default is TODO add default
     */
    public final void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    /**
     * Gets the ConnectionFactoryResource value of connectionResource for this instance of SessionPool.
     *
     * @return the connectionResource
     */
    public ConnectionResource getConnectionResource() {
        return connectionResource;
    }

    protected XAResource createXaResource(XASession session) throws JMSException {
        return session.getXAResource();
    }
    
    
//    protected class Synchronization implements javax.transaction.Synchronization {
//        private final XASession session;
//
//        private Synchronization(XASession session) {
//            this.session = session;
//        }
//
//        public void beforeCompletion() {
//        }
//        
//        public void afterCompletion(int status) {
//            try {
//                // This will return session to the pool.
//                session.setIgnoreClose(false);
//                session.close();
//                session.setIsXa(false);
//            } catch (JMSException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
}
