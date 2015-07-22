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
package org.apache.camel.component.interactivebrokers;

import java.net.URI;

import com.ib.client.Contract;
import com.ib.client.Types.SecType;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class InteractiveBrokersConfiguration implements Cloneable {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 7496;
    public static final int DEFAULT_CLIENT_ID = 0;
    
    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersConfiguration.class);

    @UriPath
    private String host = DEFAULT_HOST;

    @UriPath
    private int port = DEFAULT_PORT;

    @UriParam(defaultValue = "0")
    private int clientId = DEFAULT_CLIENT_ID;
    
    @UriParam(defaultValue = "marketDataTop")
    private InteractiveBrokersConsumerType consumerType;

    @UriParam(defaultValue = "orders")
    private InteractiveBrokersProducerType producerType;

    @UriParam
    private Contract contract;
    
    @UriParam
    private String symbol;
    
    @UriParam
    private String currency;
    
    @UriParam
    private String exchangeName;
    
    @UriParam
    private SecType securityType;

    @UriParam
    private int tradeReportPollingFrequency
        = InteractiveBrokersTradeReportRealTimeConsumer.DEFAULT_POLL_FREQUENCY;

    /**
     * Returns a copy of this configuration
     */
    public InteractiveBrokersConfiguration copy() {
        try {
            InteractiveBrokersConfiguration copy = (InteractiveBrokersConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void configure(URI uri) {
        String value = uri.getHost();
        if (value != null) {
            setHost(value);
        }
        int port = uri.getPort();
        if (port > 0) {
            setPort(port);
        }
    }

    public InteractiveBrokersTransportTuple getTransportTuple() {
        return new InteractiveBrokersTransportTuple(getHost(), getPort(), getClientId());
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }
    
    public InteractiveBrokersConsumerType getConsumerType() {
        return consumerType;
    }

    public void setConsumerType(InteractiveBrokersConsumerType consumerType) {
        this.consumerType = consumerType;
    }

    public InteractiveBrokersProducerType getProducerType() {
        return producerType;
    }

    public void setProducerType(InteractiveBrokersProducerType producerType) {
        this.producerType = producerType;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public SecType getSecurityType() {
        return securityType;
    }

    public void setSecurityType(SecType securityType) {
        this.securityType = securityType;
    }

    public int getTradeReportPollingFrequency() {
        return tradeReportPollingFrequency;
    }

    public void setTradeReportPollingFrequency(int tradeReportPollingFrequency) {
        this.tradeReportPollingFrequency = tradeReportPollingFrequency;
    }

    @Override
    public String toString() {
        return "InteractiveBrokersConfiguration["
                + "host=" + host + ":" + port
                + ", clientId=" + clientId
                + ", consumerType=" + consumerType
                + ", producerType=" + producerType
                + ", tradeReportPollingFrequency=" + tradeReportPollingFrequency
                + "]";
    }

}
