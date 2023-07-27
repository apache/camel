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
package org.apache.camel.component.xchange;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

// Wraps the exchange to avoid name collision with the camel exchange
public class XChange implements Exchange {

    private final Exchange delegate;

    public XChange(Exchange delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return delegate.getExchangeSpecification();
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return delegate.getExchangeMetaData();
    }

    @Override
    public List<Instrument> getExchangeInstruments() {
        return delegate.getExchangeInstruments();
    }

    public List<CurrencyPair> getCurrencyPairs() {
        return delegate.getExchangeInstruments().stream()
                .filter(it -> it instanceof CurrencyPair)
                .map(it -> (CurrencyPair) it)
                .collect(Collectors.toList());
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return delegate.getNonceFactory();
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return delegate.getDefaultExchangeSpecification();
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        delegate.applySpecification(exchangeSpecification);
    }

    @Override
    public MarketDataService getMarketDataService() {
        return delegate.getMarketDataService();
    }

    @Override
    public TradeService getTradeService() {
        return delegate.getTradeService();
    }

    @Override
    public AccountService getAccountService() {
        return delegate.getAccountService();
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {
        delegate.remoteInit();
    }
}
