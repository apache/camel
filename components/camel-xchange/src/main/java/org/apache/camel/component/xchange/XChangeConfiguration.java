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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

@UriParams
public class XChangeConfiguration {

    // Available service
    public enum XChangeService {
        marketdata,
        metadata,
        account
    }

    // Available methods
    public enum XChangeMethod {
        // Account service methods
        balances(XChangeService.account),
        fundingHistory(XChangeService.account),
        wallets(XChangeService.account),
        // Metadata service methods
        currencies(XChangeService.metadata),
        currencyMetaData(XChangeService.metadata),
        currencyPairs(XChangeService.metadata),
        currencyPairMetaData(XChangeService.metadata),
        // Marketdata service methods
        ticker(XChangeService.marketdata);

        private final XChangeService service;

        XChangeMethod(XChangeService service) {
            this.service = service;
        }

        /**
         * The service the method belongs to
         */
        public XChangeService getService() {
            return service;
        }
    }

    @Metadata(description = "The currency for the currencyMetaData method, as an XChange Currency or as text such as BTC. It"
                            + " replaces the message body and the currency option. Text with a currency that XChange does not"
                            + " know fails the exchange.",
              javaType = "org.knowm.xchange.currency.Currency")
    public static final String HEADER_CURRENCY = "Currency";
    @Metadata(description = "The currency pair for the ticker and currencyPairMetaData methods, as an XChange CurrencyPair or"
                            + " as text such as BTC/USDT. It replaces the message body and the currencyPair option. Text with a"
                            + " currency that XChange does not know fails the exchange.",
              javaType = "org.knowm.xchange.currency.CurrencyPair")
    public static final String HEADER_CURRENCY_PAIR = "CurrencyPair";

    @UriPath(description = "The name of the exchange in XChange, for example binance, for which the component loads the"
                           + " class org.knowm.xchange.binance.BinanceExchange. The XChange module of the exchange must be on"
                           + " the classpath, and camel-xchange includes only the one of Binance.")
    @Metadata(required = true)
    private String name;
    @UriParam(description = "The XChange service that the method belongs to. Creating the endpoint fails if the method"
                            + " belongs to another service.")
    @Metadata(required = true)
    private XChangeService service;
    @UriParam(description = "The method to call, and its result is the message body. The account service has balances,"
                            + " fundingHistory and wallets, the marketdata service has ticker, and the metadata service has"
                            + " currencies, currencyMetaData, currencyPairs and currencyPairMetaData.")
    @Metadata(required = true)
    private XChangeMethod method;
    @UriParam(description = "The currency, such as BTC, for the currencyMetaData method when neither the Currency header nor"
                            + " the message body gives one. A currency that XChange does not know fails the creation of the"
                            + " endpoint.")
    private Currency currency;
    @UriParam(description = "The currency pair, such as BTC/USDT, for the ticker and currencyPairMetaData methods when"
                            + " neither the CurrencyPair header nor the message body gives one. A currency that XChange does"
                            + " not know fails the creation of the endpoint.")
    private String currencyPair;
    @UriParam(label = "security", security = "secret",
              description = "The API key of the account at the exchange, for the account service. If not set, the exchange"
                            + " module of XChange decides, for example the one of Binance reads it from the file"
                            + " binance-secret.keys.")
    private String apiKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret key of the account at the exchange, for the account service. If not set, the"
                            + " exchange module of XChange decides, for example the one of Binance reads it from the file"
                            + " binance-secret.keys.")
    private String secretKey;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public XChangeService getService() {
        return service;
    }

    public void setService(XChangeService service) {
        this.service = service;
    }

    public XChangeMethod getMethod() {
        return method;
    }

    public void setMethod(XChangeMethod method) {
        this.method = method;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    /**
     * Sets the currency from its code, or null if XChange does not know the currency.
     *
     * @deprecated Camel does not call this method, the currency option is converted with
     *             {@link XChangeConverters#toCurrency(String)}, which fails for an unknown currency.
     */
    @Deprecated(since = "4.24.0")
    public void setCurrency(String curr) {
        this.currency = Currency.getInstanceNoCreate(curr);
    }

    /**
     * The currency pair option as an XChange {@link CurrencyPair}, or null if the option is not set.
     *
     * @throws IllegalArgumentException if a currency of the pair is not known to XChange
     */
    public CurrencyPair getAsCurrencyPair() {
        if (currencyPair != null) {
            return XChangeConverters.toCurrencyPair(currencyPair);
        }
        return null;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
