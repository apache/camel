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
package org.knowm.xchange.camelkeys;

import java.util.List;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * An exchange module for the tests, found by camel-xchange for the name camelkeys as XChange modules are. It keeps the
 * specification it is given, makes no remote call, and registers the currency CMLK when it is created, as an exchange
 * module registers the currencies that it trades.
 */
public class CamelkeysExchange implements Exchange {

    public static final String CURRENCY = "CMLK";

    private ExchangeSpecification specification;

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        ExchangeSpecification answer = new ExchangeSpecification(getClass());
        answer.setExchangeName("camelkeys");
        answer.setShouldLoadRemoteMetaData(false);
        return answer;
    }

    @Override
    public void applySpecification(ExchangeSpecification specification) {
        this.specification = specification;
        Currency.getInstance(CURRENCY);
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return specification;
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return null;
    }

    @Override
    public List<Instrument> getExchangeInstruments() {
        return List.of();
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return null;
    }

    @Override
    public MarketDataService getMarketDataService() {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public AccountService getAccountService() {
        return null;
    }

    @Override
    public void remoteInit() {
        // no remote call
    }
}
