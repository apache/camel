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
import org.apache.camel.impl.DefaultProducer;
import org.jsmpp.DefaultPDUReader;
import org.jsmpp.DefaultPDUSender;
import org.jsmpp.SynchronizedPDUSender;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.DefaultComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of @{link Producer} which use the SMPP protocol
 * 
 * @version 
 * @author muellerc
 */
public class SmppProducer extends DefaultProducer {

    private static final transient Logger LOG = LoggerFactory.getLogger(SmppProducer.class);

    private SmppConfiguration configuration;
    private SMPPSession session;
    private SessionStateListener sessionStateListener;
    private final ReentrantLock reconnectLock = new ReentrantLock();

    public SmppProducer(SmppEndpoint endpoint, SmppConfiguration config) {
        super(endpoint);
        this.configuration = config;
        this.sessionStateListener = new SessionStateListener() {
            public void onStateChange(SessionState newState, SessionState oldState, Object source) {
                if (newState.equals(SessionState.CLOSED)) {
                    LOG.warn("Loosing connection to: " + getEndpoint().getConnectionString() + " - trying to reconnect...");
                    closeSession(session);
                    reconnect(configuration.getInitialReconnectDelay());
                }
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
        session.setEnquireLinkTimer(this.configuration.getEnquireLinkTimer());
        session.setTransactionTimer(this.configuration.getTransactionTimer());
        session.addSessionStateListener(sessionStateListener);
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
        
        return session;
    }
    
    /**
     * Factory method to easily instantiate a mock SMPPSession
     * 
     * @return the SMPPSession
     */
    SMPPSession createSMPPSession() {
        if (configuration.getUsingSSL()) {
            return new SMPPSession(new SynchronizedPDUSender(new DefaultPDUSender(new DefaultComposer())),
                                   new DefaultPDUReader(), SmppSSLConnectionFactory.getInstance());
        } else {
            return new SMPPSession();
        }
    }

    public void process(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending a short message for exchange id '"
                    + exchange.getExchangeId() + "'...");
        }
        
        // only possible by trying to reconnect 
        if (this.session == null) {
            throw new IOException("Lost connection to " + getEndpoint().getConnectionString() + " and yet not reconnected");
        }

        SubmitSm submitSm = getEndpoint().getBinding().createSubmitSm(exchange);
        String messageId = session.submitShortMessage(
                submitSm.getServiceType(), 
                TypeOfNumber.valueOf(submitSm.getSourceAddrTon()),
                NumberingPlanIndicator.valueOf(submitSm.getSourceAddrNpi()),
                submitSm.getSourceAddr(),
                TypeOfNumber.valueOf(submitSm.getDestAddrTon()),
                NumberingPlanIndicator.valueOf(submitSm.getDestAddrNpi()),
                submitSm.getDestAddress(),
                new ESMClass(),
                submitSm.getProtocolId(),
                submitSm.getPriorityFlag(),
                submitSm.getScheduleDeliveryTime(),
                submitSm.getValidityPeriod(),
                new RegisteredDelivery(submitSm.getRegisteredDelivery()),
                submitSm.getReplaceIfPresent(),
                new GeneralDataCoding(
                        false,
                        false,
                        MessageClass.CLASS1,
                        Alphabet.valueOf(submitSm.getDataCoding())),
                (byte) 0,
                submitSm.getShortMessage());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sent a short message for exchange id '"
                    + exchange.getExchangeId() + "' and received message id '"
                    + messageId + "'");
        }

        if (exchange.getPattern().isOutCapable()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exchange is out capable, setting headers on out exchange...");
            }
            exchange.getOut().setHeader(SmppBinding.ID, messageId);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exchange is not out capable, setting headers on in exchange...");
            }
            exchange.getIn().setHeader(SmppBinding.ID, messageId);
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
            session.unbindAndClose();
            // throws the java.util.ConcurrentModificationException
            //session.removeSessionStateListener(this.sessionStateListener);
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
