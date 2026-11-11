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

import org.apache.camel.Converter;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

/**
 * Converts the text of a currency, such as BTC, and of a currency pair, such as BTC/USDT, to the XChange
 * {@link Currency} and {@link CurrencyPair}.
 * <p>
 * Only currencies XChange knows are converted: the predefined ones, and the ones of the exchanges that are loaded.
 * Unknown text fails rather than registers a new currency, as XChange keeps the currencies in a JVM wide registry.
 */
@Converter(generateLoader = true)
public final class XChangeConverters {

    private XChangeConverters() {
        // Utility Class
    }

    @Converter
    public static Currency toCurrency(String code) {
        Currency answer = Currency.getInstanceNoCreate(code.trim());
        if (answer == null) {
            throw new IllegalArgumentException("Unknown currency: " + code);
        }
        return answer;
    }

    @Converter
    public static CurrencyPair toCurrencyPair(String pair) {
        String text = pair.trim();
        // the same separators as new CurrencyPair(String)
        int pos = text.contains("-") ? text.indexOf('-') : text.indexOf('/');
        if (pos < 1 || pos == text.length() - 1) {
            throw new IllegalArgumentException("Could not parse currency pair from: " + pair);
        }
        return new CurrencyPair(toCurrency(text.substring(0, pos)), toCurrency(text.substring(pos + 1)));
    }
}
