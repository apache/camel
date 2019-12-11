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

import org.apache.camel.Exchange;
import org.apache.camel.component.xchange.XChangeConfiguration.XChangeMethod;
import org.apache.camel.support.DefaultProducer;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY;
import static org.apache.camel.component.xchange.XChangeConfiguration.HEADER_CURRENCY_PAIR;

public class XChangeMetaDataProducer extends DefaultProducer {
    
    public XChangeMetaDataProducer(XChangeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public XChangeEndpoint getEndpoint() {
        return (XChangeEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        XChangeEndpoint endpoint = getEndpoint();
        XChangeMethod method = endpoint.getConfiguration().getMethod();
        
        if (XChangeMethod.currencies == method) {
            Object body = endpoint.getCurrencies();
            exchange.getMessage().setBody(body);
        } else if (XChangeMethod.currencyPairs == method) {
            Object body = endpoint.getCurrencyPairs();
            exchange.getMessage().setBody(body);
        } else if (XChangeMethod.currencyMetaData == method) {
            Currency curr = exchange.getMessage().getHeader(HEADER_CURRENCY, Currency.class);
            curr = curr != null ? curr : exchange.getMessage().getBody(Currency.class);
            curr = curr != null ? curr : endpoint.getConfiguration().getCurrency();
            Object body = endpoint.getCurrencyMetaData(curr);
            exchange.getMessage().setBody(body);
        } else if (XChangeMethod.currencyPairMetaData == method) {
            CurrencyPair pair = exchange.getIn().getHeader(HEADER_CURRENCY_PAIR, CurrencyPair.class);
            pair = pair != null ? pair : exchange.getMessage().getBody(CurrencyPair.class);
            pair = pair != null ? pair : endpoint.getConfiguration().getAsCurrencyPair();
            Object body = endpoint.getCurrencyPairMetaData(pair);
            exchange.getMessage().setBody(body);
        }
    }
}
