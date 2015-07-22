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

import com.ib.client.Contract;
import com.ib.client.TickType;
import com.ib.client.Types.MktDataType;
import com.ib.client.Types.SecType;
import com.ib.controller.ApiController.ITopMktDataHandler;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveBrokersMarketDataTopRealTimeConsumer extends InteractiveBrokersConsumer implements ITopMktDataHandler  {

    public static final String TYPE_NAME = "marketDataTop";

    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersMarketDataTopRealTimeConsumer.class);
    
    private final Contract contract;
    private final String symbol;
    private final String currency;
    private final String exchangeName;
    private final SecType securityType;

    public InteractiveBrokersMarketDataTopRealTimeConsumer(InteractiveBrokersEndpoint endpoint, Processor processor, InteractiveBrokersBinding binding) {
        super(endpoint, processor, binding);
        
        InteractiveBrokersConfiguration cfg = endpoint.getConfiguration();
        cfg.getContract();
        if (cfg.getContract() == null) {
            // Use individual values from URI to build the Contract
            contract = new Contract();
            symbol = cfg.getSymbol();
            currency = cfg.getCurrency();
            exchangeName = cfg.getExchangeName();
            securityType = cfg.getSecurityType();
            // Using localSymbol with securityType CASH doesn't seem to work
            if (securityType != SecType.CASH) {
                contract.localSymbol(symbol);
            }
            contract.symbol(symbol);
            contract.currency(currency);
            contract.exchange(exchangeName);
            contract.secType(securityType);            
        } else {
            // Use the Contract to obtain values we will put in
            // the Message headers
            contract = cfg.getContract();
            symbol = contract.symbol();
            currency = contract.currency();
            exchangeName = contract.exchange();
            securityType = contract.secType();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        getBinding().getApiController().reqTopMktData(contract, "", false, this);
        
    }
    
    @Override
    protected void doStop() throws Exception {
        getBinding().getApiController().cancelTopMktData(this);
        super.doStop();
    }

    @Override
    public void tickPrice(TickType tickType, double price, int canAutoExecute) {

        final Exchange exchange = getEndpoint().createExchange();
        Message in = exchange.getIn();

        in.setHeader(InteractiveBrokersConstants.SYMBOL, symbol);
        in.setHeader(InteractiveBrokersConstants.CURRENCY, currency);
        in.setHeader(InteractiveBrokersConstants.EXCHANGE_NAME, exchangeName);
        in.setHeader(InteractiveBrokersConstants.SECURITY_TYPE, securityType);
        in.setHeader(InteractiveBrokersConstants.TICK_TYPE, tickType);
        
        in.setBody(price);

        try {
            // Standard synchronous processing:
            // getProcessor().process(exchange);

            // Support for asynchronous processing
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                    if (log.isTraceEnabled()) {
                        log.trace("Done processing tick for: {} {}", symbol,
                                doneSync ? "synchronously" : "asynchronously");
                    }
                }
            });

        } catch (Exception e) {
            handleException(String.format("Error processing %s: %s", exchange, e.getMessage()), e);
        } finally {
            Exception ex = exchange.getException();
            if (ex != null) {
                handleException(String.format("Unhandled exception: %s", ex.getMessage()), ex);
            }
        }
        
    }

    @Override
    public void tickSize(TickType tickType, int size) {
        // TODO Auto-generated method stub
    }

    @Override
    public void tickString(TickType tickType, String value) {
        // TODO Auto-generated method stub
    }

    @Override
    public void tickSnapshotEnd() {
        // TODO Auto-generated method stub
    }

    @Override
    public void marketDataType(MktDataType marketDataType) {
        // TODO Auto-generated method stub
        logger.trace("marketDataType: {}", marketDataType.toString());
    }    
}