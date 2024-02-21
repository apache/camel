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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.xchange.XChangeConfiguration.XChangeService;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.binance.service.BinanceAccountService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.utils.Assert;

/**
 * Access market data and trade on Bitcoin and Altcoin exchanges.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "xchange", title = "XChange", syntax = "xchange:name", producerOnly = true,
             category = { Category.BLOCKCHAIN }, headersClass = XChangeConfiguration.class)
public class XChangeEndpoint extends DefaultEndpoint {

    @UriParam
    private XChangeConfiguration configuration;
    private transient XChange xchange;

    public XChangeEndpoint(String uri, XChangeComponent component, XChangeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public XChangeComponent getComponent() {
        return (XChangeComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer producer = null;

        XChangeService service = getConfiguration().getService();
        if (XChangeService.account == service) {
            producer = new XChangeAccountProducer(this);
        } else if (XChangeService.marketdata == service) {
            producer = new XChangeMarketDataProducer(this);
        } else if (XChangeService.metadata == service) {
            producer = new XChangeMetaDataProducer(this);
        }

        Assert.notNull(producer, "Unsupported service: " + service);
        return producer;
    }

    public void setConfiguration(XChangeConfiguration configuration) {
        this.configuration = configuration;
    }

    public XChangeConfiguration getConfiguration() {
        return configuration;
    }

    public XChange getXchange() {
        return xchange;
    }

    public void setXchange(XChange xchange) {
        this.xchange = xchange;
    }

    public List<Currency> getCurrencies() {
        ExchangeMetaData metaData = xchange.getExchangeMetaData();
        return metaData.getCurrencies().keySet().stream().sorted().collect(Collectors.toList());
    }

    public CurrencyMetaData getCurrencyMetaData(Currency curr) {
        Assert.notNull(curr, "Null currency");
        ExchangeMetaData metaData = xchange.getExchangeMetaData();
        return metaData.getCurrencies().get(curr);
    }

    public List<CurrencyPair> getCurrencyPairs() {
        ExchangeMetaData metaData = xchange.getExchangeMetaData();
        return metaData.getInstruments().keySet().stream()
                .filter(it -> it instanceof CurrencyPair)
                .map(it -> (CurrencyPair) it)
                .sorted().collect(Collectors.toList());
    }

    public InstrumentMetaData getCurrencyPairMetaData(CurrencyPair pair) {
        Assert.notNull(pair, "Null currency");
        ExchangeMetaData metaData = xchange.getExchangeMetaData();
        return metaData.getInstruments().get(pair);
    }

    public List<Balance> getBalances() throws IOException {
        List<Balance> balances = new ArrayList<>();
        getWallets().stream().forEach(w -> {
            for (Balance aux : w.getBalances().values()) {
                Currency curr = aux.getCurrency();
                CurrencyMetaData metaData = getCurrencyMetaData(curr);
                if (metaData != null) {
                    int scale = metaData.getScale();
                    double total = aux.getTotal().doubleValue();
                    double scaledTotal = total * Math.pow(10, scale / 2);
                    if (1 <= scaledTotal) {
                        balances.add(aux);
                    }
                }
            }
        });
        return balances.stream().sorted((Balance o1, Balance o2) -> o1.getCurrency().compareTo(o2.getCurrency()))
                .collect(Collectors.toList());
    }

    public List<FundingRecord> getFundingHistory() throws IOException {
        AccountService accountService = xchange.getAccountService();
        TradeHistoryParams fundingHistoryParams = accountService.createFundingHistoryParams();
        return accountService.getFundingHistory(fundingHistoryParams).stream()
                .sorted((FundingRecord o1, FundingRecord o2) -> o1.getDate().compareTo(o2.getDate()))
                .collect(Collectors.toList());
    }

    public List<Wallet> getWallets() throws IOException {
        // [#4741] BinanceAccountService assumes futures account when not using sandbox
        // https://github.com/knowm/XChange/issues/4741
        AccountService accountService = xchange.getAccountService();
        if (accountService instanceof BinanceAccountService binanceAccountService) {
            Wallet wallet = BinanceAdapters.adaptBinanceSpotWallet(binanceAccountService.account());
            return Collections.singletonList(wallet);
        } else {
            AccountInfo accountInfo = accountService.getAccountInfo();
            return accountInfo.getWallets().values().stream().sorted(Comparator.comparing(Wallet::getName))
                    .collect(Collectors.toList());
        }
    }

    public Ticker getTicker(CurrencyPair pair) throws IOException {
        Assert.notNull(pair, "Null currency pair");
        MarketDataService marketService = xchange.getMarketDataService();
        return marketService.getTicker((Instrument) pair);
    }
}
