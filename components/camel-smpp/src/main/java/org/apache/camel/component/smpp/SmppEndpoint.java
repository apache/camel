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
package org.apache.camel.component.smpp;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;

/**
 * To send and receive SMS using a SMSC (Short Message Service Center).
 */
@UriEndpoint(firstVersion = "2.2.0", scheme = "smpp,smpps", title = "SMPP", syntax = "smpp:host:port",
        label = "mobile", lenientProperties = true)
public class SmppEndpoint extends DefaultEndpoint {

    private SmppBinding binding;
    @UriParam
    private SmppConfiguration configuration;

    public SmppEndpoint(String endpointUri, Component component, SmppConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    protected String createEndpointUri() {
        return getConnectionString();
    }
    
    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SmppConsumer answer = new SmppConsumer(this, configuration, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SmppProducer(this, configuration);
    }

    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     *
     * @param alertNotification the received message from the SMSC
     * @return a new exchange
     */
    public Exchange createOnAcceptAlertNotificationExchange(AlertNotification alertNotification) {
        return createOnAcceptAlertNotificationExchange(getExchangePattern(), alertNotification);
    }
    
    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     * with the specified {@link ExchangePattern} such as whether its going
     * to be an {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut} exchange
     *
     * @param exchangePattern the message exchange pattern for the exchange
     * @param alertNotification the received message from the SMSC
     * @return a new exchange
     */
    public Exchange createOnAcceptAlertNotificationExchange(ExchangePattern exchangePattern,
                                                            AlertNotification alertNotification) {
        Exchange exchange = createExchange(exchangePattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(getBinding().createSmppMessage(getCamelContext(), alertNotification));
        return exchange;
    }

    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     *
     * @param deliverSm the received message from the SMSC
     * @return a new exchange
     */
    public Exchange createOnAcceptDeliverSmExchange(DeliverSm deliverSm) throws Exception {
        return createOnAcceptDeliverSmExchange(getExchangePattern(), deliverSm);
    }
    
    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     * with the specified {@link ExchangePattern} such as whether its going
     * to be an {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut} exchange
     *
     * @param exchangePattern the message exchange pattern for the exchange
     * @param deliverSm the received message from the SMSC
     * @return a new exchange
     */
    public Exchange createOnAcceptDeliverSmExchange(ExchangePattern exchangePattern,
                                                    DeliverSm deliverSm) throws Exception {
        Exchange exchange = createExchange(exchangePattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(getBinding().createSmppMessage(getCamelContext(), deliverSm));
        return exchange;
    }
    
    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     *
     * @param dataSm the received message from the SMSC
     * @param smppMessageId the smpp message id which will be used in the response
     * @return a new exchange
     */
    public Exchange createOnAcceptDataSm(DataSm dataSm, String smppMessageId) {
        return createOnAcceptDataSm(getExchangePattern(), dataSm, smppMessageId);
    }
    
    /**
     * Create a new exchange for communicating with this endpoint from a SMSC
     * with the specified {@link ExchangePattern} such as whether its going
     * to be an {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut} exchange
     *
     * @param exchangePattern the message exchange pattern for the exchange
     * @param dataSm the received message from the SMSC
     * @param smppMessageId the smpp message id which will be used in the response
     * @return a new exchange
     */
    public Exchange createOnAcceptDataSm(ExchangePattern exchangePattern, DataSm dataSm, String smppMessageId) {
        Exchange exchange = createExchange(exchangePattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(getBinding().createSmppMessage(getCamelContext(), dataSm, smppMessageId));
        return exchange;
    }

    /**
     * Returns the connection string for the current connection which has the form:
     * smpp://<user>@<host>:<port>
     * 
     * @return the connection string
     */
    public String getConnectionString() {
        return (configuration.isUsingSSL() ? "smpps://" : "smpp://")
                + (getConfiguration().getSystemId() != null ? getConfiguration().getSystemId() + "@" : "")
                + getConfiguration().getHost() + ":"
                + getConfiguration().getPort();
    }

    /**
     * Returns the smpp configuration
     * 
     * @return the configuration
     */
    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    public SmppBinding getBinding() {
        if (binding == null) {
            binding = new SmppBinding(getConfiguration());
        }
        return binding;
    }

    public void setBinding(SmppBinding binding) {
        this.binding = binding;
    }
}
