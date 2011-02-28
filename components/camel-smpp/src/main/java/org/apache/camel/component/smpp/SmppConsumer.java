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
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.jsmpp.DefaultPDUReader;
import org.jsmpp.DefaultPDUSender;
import org.jsmpp.SynchronizedPDUSender;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.DefaultComposer;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of @{link Consumer} which use the SMPP protocol
 * 
 * @version 
 * @author muellerc
 */
public class SmppConsumer extends DefaultConsumer {

    private static final transient Logger LOG = LoggerFactory.getLogger(SmppConsumer.class);

    private SmppConfiguration configuration;
    private SMPPSession session;
    private MessageReceiverListener messageReceiverListener;
    private SessionStateListener sessionStateListener;
    private final ReentrantLock reconnectLock = new ReentrantLock();

    /**
     * The constructor which gets a smpp endpoint, a smpp configuration and a
     * processor
     */
    public SmppConsumer(SmppEndpoint endpoint, SmppConfiguration config, Processor processor) {
        super(endpoint, processor);

        this.configuration = config;
        this.sessionStateListener = new SessionStateListener() {
            public void onStateChange(SessionState newState, SessionState oldState, Object source) {
                if (newState.equals(SessionState.CLOSED)) {
                    LOG.warn("Loost connection to: " + getEndpoint().getConnectionString()
                            + " - trying to reconnect...");
                    closeSession(session);
                    reconnect(configuration.getInitialReconnectDelay());
                }
            }
        };
        this.messageReceiverListener = new MessageReceiverListener() {
            private final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();

            public void onAcceptAlertNotification(AlertNotification alertNotification) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Received an alertNotification " + alertNotification);
                }

                try {
                    Exchange exchange = getEndpoint().createOnAcceptAlertNotificationExchange(
                            alertNotification);

                    LOG.trace("Processing the new smpp exchange...");
                    getProcessor().process(exchange);
                    LOG.trace("Processed the new smpp exchange");
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                }
            }

            public void onAcceptDeliverSm(DeliverSm deliverSm) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Received a deliverSm " + deliverSm);
                }

                try {
                    Exchange exchange = getEndpoint().createOnAcceptDeliverSmExchange(deliverSm);

                    LOG.trace("processing the new smpp exchange...");
                    getProcessor().process(exchange);
                    LOG.trace("processed the new smpp exchange");
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                }
            }

            public DataSmResult onAcceptDataSm(DataSm dataSm, Session session)
                throws ProcessRequestException {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Received a dataSm " + dataSm);
                }

                MessageId newMessageId = messageIDGenerator.newMessageId();

                try {
                    Exchange exchange = getEndpoint().createOnAcceptDataSm(dataSm,
                            newMessageId.getValue());

                    LOG.trace("processing the new smpp exchange...");
                    getProcessor().process(exchange);
                    LOG.trace("processed the new smpp exchange");
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                    throw new ProcessRequestException(e.getMessage(), 255, e);
                }

                return new DataSmResult(newMessageId, dataSm.getOptionalParametes());
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Connecting to: " + getEndpoint().getConnectionString() + "...");

        super.doStart();
        session = createSession();

        LOG.info("Connected to: " + getEndpoint().getConnectionString());
    }

    private SMPPSession createSession() throws IOException {
        SMPPSession session = createSMPPSession();
        session.setEnquireLinkTimer(configuration.getEnquireLinkTimer());
        session.setTransactionTimer(configuration.getTransactionTimer());
        session.addSessionStateListener(sessionStateListener);
        session.setMessageReceiverListener(messageReceiverListener);
        session.connectAndBind(this.configuration.getHost(), this.configuration.getPort(),
                new BindParameter(BindType.BIND_RX, this.configuration.getSystemId(),
                        this.configuration.getPassword(), this.configuration.getSystemType(),
                        TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, ""));
        
        return session;
    }
    
    /**
     * Factory method to easily instantiate a mock SMPPSession
     * 
     * @return the SMPPSession
     */
    SMPPSession createSMPPSession() {
        if (configuration.getUsingSSL()) {
            return new SMPPSession(new SynchronizedPDUSender(new DefaultPDUSender(
                    new DefaultComposer())), new DefaultPDUReader(), SmppSSLConnectionFactory
                    .getInstance());
        } else {
            return new SMPPSession();
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Disconnecting from: " + getEndpoint().getConnectionString() + "...");

        super.doStop();
        closeSession(session);

        LOG.info("Disconnected from: " + getEndpoint().getConnectionString());
    }

    private void closeSession(SMPPSession session) {
        if (session != null) {
            session.removeSessionStateListener(this.sessionStateListener);
            // remove this hack after http://code.google.com/p/jsmpp/issues/detail?id=93 is fixed
            try {
                Thread.sleep(1000);
                session.unbindAndClose();
            } catch (Exception e) {
                LOG.warn("Could not close session " + session);
            }
            session = null;
        }
    }

    private void reconnect(final long initialReconnectDelay) {
        if (reconnectLock.tryLock()) {
            try {
                Runnable r = new Runnable() {
                    public void run() {
                        boolean reconnected = false;
                        
                        LOG.info("Schedule reconnect after " + initialReconnectDelay + " millis");
                        try {
                            Thread.sleep(initialReconnectDelay);
                        } catch (InterruptedException e) {
                        }

                        int attempt = 0;
                        while (!(isStopping() || isStopped()) && (session == null || session.getSessionState().equals(SessionState.CLOSED))) {
                            try {
                                LOG.info("Trying to reconnect to " + getEndpoint().getConnectionString() + " - attempt #" + (++attempt) + "...");
                                session = createSession();
                                reconnected = true;
                            } catch (IOException e) {
                                LOG.info("Failed to reconnect to " + getEndpoint().getConnectionString());
                                closeSession(session);
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
                reconnectLock.unlock();
            }
        }
    }

    @Override
    public String toString() {
        return "SmppConsumer[" + getEndpoint().getConnectionString() + "]";
    }

    @Override
    public SmppEndpoint getEndpoint() {
        return (SmppEndpoint) super.getEndpoint();
    }

    /**
     * Returns the smpp configuration
     * 
     * @return the configuration
     */
    public SmppConfiguration getConfiguration() {
        return configuration;
    }
}
