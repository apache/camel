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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.jsmpp.DefaultPDUReader;
import org.jsmpp.DefaultPDUSender;
import org.jsmpp.SynchronizedPDUSender;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.DefaultComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of @{link Producer} which use the SMPP protocol
 *
 * @version
 */
public class SmppProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SmppProducer.class);

    private SmppConfiguration configuration;
    private SMPPSession session;
    private SessionStateListener internalSessionStateListener;
    private final ReentrantLock connectLock = new ReentrantLock();

    public SmppProducer(SmppEndpoint endpoint, SmppConfiguration config) {
        super(endpoint);
        this.configuration = config;
        this.internalSessionStateListener = new SessionStateListener() {
            @Override
            public void onStateChange(SessionState newState, SessionState oldState, Session source) {
                if (configuration.getSessionStateListener() != null) {
                    configuration.getSessionStateListener().onStateChange(newState, oldState, source);
                }
                
                if (newState.equals(SessionState.CLOSED)) {
                    LOG.warn("Lost connection to: {} - trying to reconnect...", getEndpoint().getConnectionString());
                    closeSession();
                    reconnect(configuration.getInitialReconnectDelay());
                }
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        
        if (!getConfiguration().isLazySessionCreation()) {
            if (connectLock.tryLock()) {
                try {
                    session = createSession();
                } finally {
                    connectLock.unlock();
                }
            }
        }
    }

    private SMPPSession createSession() throws IOException {
        LOG.debug("Connecting to: {}...", getEndpoint().getConnectionString());
        
        SMPPSession session = createSMPPSession();
        session.setEnquireLinkTimer(this.configuration.getEnquireLinkTimer());
        session.setTransactionTimer(this.configuration.getTransactionTimer());
        session.addSessionStateListener(internalSessionStateListener);
        session.connectAndBind(
                this.configuration.getHost(),
                this.configuration.getPort(),
                new BindParameter(
                        BindType.BIND_TX,
                        this.configuration.getSystemId(),
                        this.configuration.getPassword(), 
                        this.configuration.getSystemType(),
                        TypeOfNumber.valueOf(configuration.getTypeOfNumber()),
                        NumberingPlanIndicator.valueOf(configuration.getNumberingPlanIndicator()),
                        ""));
        
        LOG.info("Connected to: {}", getEndpoint().getConnectionString());
        
        return session;
    }

    /**
     * Factory method to easily instantiate a mock SMPPSession
     * 
     * @return the SMPPSession
     */
    SMPPSession createSMPPSession() {
        return new SMPPSession(new SynchronizedPDUSender(new DefaultPDUSender(
                new DefaultComposer())), new DefaultPDUReader(), SmppConnectionFactory
                .getInstance(configuration));
    }

    public void process(Exchange exchange) throws Exception {
        if (session == null) {
            if (this.configuration.isLazySessionCreation()) {
                if (connectLock.tryLock()) {
                    try {
                        if (session == null) {
                            // set the system id and password with which we will try to connect to the SMSC
                            Message in = exchange.getIn();
                            String systemId = in.getHeader(SmppConstants.SYSTEM_ID, String.class);
                            String password = in.getHeader(SmppConstants.PASSWORD, String.class);
                            if (systemId != null && password != null) {
                                log.info("using the system id '{}' to connect to the SMSC...", systemId);
                                this.configuration.setSystemId(systemId);
                                this.configuration.setPassword(password);
                            }
                            session = createSession();
                        }
                    } finally {
                        connectLock.unlock();
                    }
                }
            }
        }
        
        // only possible by trying to reconnect 
        if (this.session == null) {
            throw new IOException("Lost connection to " + getEndpoint().getConnectionString() + " and yet not reconnected");
        }
        
        SmppCommand command = getEndpoint().getBinding().createSmppCommand(session, exchange);
        command.execute(exchange);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Disconnecting from: {}...", getEndpoint().getConnectionString());

        super.doStop();
        closeSession();

        LOG.info("Disconnected from: {}", getEndpoint().getConnectionString());
    }
    
    private void closeSession() {
        if (session != null) {
            session.removeSessionStateListener(this.internalSessionStateListener);
            session.unbindAndClose();
            // clear session as we closed it successfully
            session = null;
        }
    }

    private void reconnect(final long initialReconnectDelay) {
        if (connectLock.tryLock()) {
            try {
                Runnable r = new Runnable() {
                    public void run() {
                        boolean reconnected = false;
                        
                        LOG.info("Schedule reconnect after {} millis", initialReconnectDelay);
                        try {
                            Thread.sleep(initialReconnectDelay);
                        } catch (InterruptedException e) {
                        }

                        int attempt = 0;
                        while (!(isStopping() || isStopped()) && (session == null || session.getSessionState().equals(SessionState.CLOSED))
                                && attempt < configuration.getMaxReconnect()) {
                            try {
                                attempt++;
                                LOG.info("Trying to reconnect to {} - attempt #{}", getEndpoint().getConnectionString(), attempt);
                                session = createSession();
                                reconnected = true;
                            } catch (IOException e) {
                                LOG.warn("Failed to reconnect to {}", getEndpoint().getConnectionString());
                                closeSession();
                                try {
                                    Thread.sleep(configuration.getReconnectDelay());
                                } catch (InterruptedException ee) {
                                }
                            }
                        }
                        
                        if (reconnected) {
                            LOG.info("Reconnected to " + getEndpoint().getConnectionString());                        
                        }
                    }
                };
                
                Thread t = new Thread(r);
                t.start(); 
                t.join();
            } catch (InterruptedException e) {
                // noop
            }  finally {
                connectLock.unlock();
            }
        }
    }
    
    @Override
    public SmppEndpoint getEndpoint() {
        return (SmppEndpoint) super.getEndpoint();
    }

    /**
     * Returns the smppConfiguration for this producer
     * 
     * @return the configuration
     */
    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "SmppProducer[" + getEndpoint().getConnectionString() + "]";
    }
}
