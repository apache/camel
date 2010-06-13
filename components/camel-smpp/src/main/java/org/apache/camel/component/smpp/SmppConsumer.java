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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.util.DefaultComposer;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;

/**
 * An implementation of @{link Consumer} which use the SMPP protocol
 * 
 * @version $Revision$
 * @author muellerc
 */
public class SmppConsumer extends DefaultConsumer {

    private static final transient Log LOG = LogFactory.getLog(SmppConsumer.class);

    private SmppConfiguration configuration;
    private SMPPSession session;

    /**
     * The constructor which gets a smpp endpoint, a smpp configuration and a processor
     */
    public SmppConsumer(SmppEndpoint endpoint, SmppConfiguration configuration, Processor processor) {
        super(endpoint, processor);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Connecting to: " + getEndpoint().getConnectionString() + "...");
        }

        super.doStart();

        session = createSMPPSession();
        session.setEnquireLinkTimer(this.configuration.getEnquireLinkTimer());
        session.setTransactionTimer(this.configuration.getTransactionTimer());
        session.setMessageReceiverListener(new MessageReceiverListener() {
            private final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();

            public void onAcceptAlertNotification(AlertNotification alertNotification) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Received an alertNotification " + alertNotification);
                }

                try {
                    Exchange exchange = getEndpoint().createOnAcceptAlertNotificationExchange(alertNotification);

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

            public DataSmResult onAcceptDataSm(DataSm dataSm, Session session)  throws ProcessRequestException {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Received a dataSm " + dataSm);
                }

                MessageId newMessageId = messageIDGenerator.newMessageId();
                
                try {
                    Exchange exchange = getEndpoint().createOnAcceptDataSm(dataSm, newMessageId.getValue());

                    LOG.trace("processing the new smpp exchange...");
                    getProcessor().process(exchange);
                    LOG.trace("processed the new smpp exchange");
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                    throw new ProcessRequestException(e.getMessage(), 255, e);
                }
                
                return new DataSmResult(newMessageId, dataSm.getOptionalParametes());
            }
        });

        session.connectAndBind(
                this.configuration.getHost(),
                this.configuration.getPort(),
                new BindParameter(
                        BindType.BIND_RX,
                        this.configuration.getSystemId(),
                        this.configuration.getPassword(),
                        this.configuration.getSystemType(),
                        TypeOfNumber.UNKNOWN,
                        NumberingPlanIndicator.UNKNOWN,
                        ""));

        LOG.info("Connected to: " + getEndpoint().getConnectionString());
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

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Disconnecting from: " + getEndpoint().getConnectionString() + "...");

        super.doStop();

        if (session != null) {
            session.close();
            session = null;
        }

        LOG.info("Disconnected from: " + getEndpoint().getConnectionString());
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