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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;

/**
 * An implementation of @{link Producer} which use the SMPP protocol
 * 
 * @version $Revision$
 * @author muellerc
 */
public class SmppProducer extends DefaultProducer {

    private static final transient Log LOG = LogFactory.getLog(SmppProducer.class);

    private SmppConfiguration configuration;
    private SMPPSession session;

    public SmppProducer(SmppEndpoint endpoint, SmppConfiguration configuration) {
        super(endpoint);
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

        LOG.info("Connected to: " + getEndpoint().getConnectionString());
    }
    
    /**
     * Factory method to easily instantiate a mock SMPPSession
     * 
     * @return the SMPPSession
     */
    SMPPSession createSMPPSession() {
        return new SMPPSession();
    }

    public void process(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending a short message for exchange id '"
                    + exchange.getExchangeId() + "'...");
        }

        SubmitSm submitSm = getEndpoint().getBinding().createSubmitSm(exchange);
        String messageId;
        try {
            messageId = doProcess(submitSm);
        } catch (Exception e) {
            // TODO: Add some DEBUG logging that we retry one more time
            doStop();
            doStart();
            
            messageId = doProcess(submitSm);
        }

        LOG.info("Sent a short message for exchange id '"
                + exchange.getExchangeId() + "' and received message id '"
                + messageId + "'");

        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setHeader(SmppBinding.ID, messageId);
        } else {
            exchange.getIn().setHeader(SmppBinding.ID, messageId);
        }
    }

    private String doProcess(SubmitSm submitSm) throws PDUException,
            ResponseTimeoutException, InvalidResponseException,
            NegativeResponseException, IOException {

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
                        Alphabet.ALPHA_DEFAULT),
                (byte) 0,
                submitSm.getShortMessage());

        return messageId;
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting from: " + getEndpoint().getConnectionString() + "...");
        }

        super.doStop();

        if (session != null) {
            session.close();
            session = null;
        }

        LOG.info("Disconnected from: " + getEndpoint().getConnectionString());
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