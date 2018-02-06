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
package org.apache.camel.component.xchange;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.xchange.XChangeConfiguration.XChangeService;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.utils.Assert;

@UriEndpoint(firstVersion = "2.21.0", scheme = "xchange", title = "XChange", syntax = "xchange:name", producerOnly = true, label = "blockchain")
public class XChangeEndpoint extends DefaultEndpoint {

    @UriParam
    private XChangeConfiguration configuration;
    private final XChange exchange;
    
    public XChangeEndpoint(String uri, XChangeComponent component, XChangeConfiguration properties, XChange exchange) {
        super(uri, component);
        this.configuration = properties;
        this.exchange = exchange;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        
        Producer producer = null;
        
        XChangeService service = getConfiguration().getService();
        if (XChangeService.metadata == service) {
            producer = new XChangeMetaDataProducer(this);
        } else if (XChangeService.marketdata == service) {
            producer = new XChangeMarketDataProducer(this);
        }
        
        Assert.notNull(producer, "Unsupported service: " + service);
        return producer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public XChangeConfiguration getConfiguration() {
        return configuration;
    }

    public XChange getXChange() {
        return exchange;
    }
    
    public List<Currency> getCurrencies() {
        ExchangeMetaData metaData = exchange.getExchangeMetaData();
        return metaData.getCurrencies().keySet().stream().sorted().collect(Collectors.toList());
    }
    
    public CurrencyMetaData getCurrencyMetaData(Currency curr) {
        Assert.notNull(curr, "Null currency");
        ExchangeMetaData metaData = exchange.getExchangeMetaData();
        return metaData.getCurrencies().get(curr);
    }
    
    public List<CurrencyPair> getCurrencyPairs() {
        ExchangeMetaData metaData = exchange.getExchangeMetaData();
        return metaData.getCurrencyPairs().keySet().stream().sorted().collect(Collectors.toList());
    }
    
    public CurrencyPairMetaData getCurrencyPairMetaData(CurrencyPair pair) {
        Assert.notNull(pair, "Null currency");
        ExchangeMetaData metaData = exchange.getExchangeMetaData();
        return metaData.getCurrencyPairs().get(pair);
    }
}
