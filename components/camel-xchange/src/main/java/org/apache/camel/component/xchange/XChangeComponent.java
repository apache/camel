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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.component.xchange.XChangeConfiguration.XChangeMethod;
import org.apache.camel.component.xchange.XChangeConfiguration.XChangeService;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.utils.Assert;

@Component("xchange")
public class XChangeComponent extends DefaultComponent {

    private final Map<String, XChange> xchanges = new ConcurrentHashMap<>();
    // the exchanges created with the API keys of an endpoint, one per exchange name and keys
    private final Map<Credentials, XChange> xchangesWithKeys = new ConcurrentHashMap<>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        XChangeConfiguration configuration = new XChangeConfiguration();
        configuration.setName(remaining);

        XChangeEndpoint endpoint = new XChangeEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        XChangeService service = configuration.getService();
        XChangeMethod method = configuration.getMethod();
        if (service == null || method == null) {
            throw new IllegalArgumentException("The service and method options are required");
        }
        if (method.getService() != service) {
            throw new IllegalArgumentException(
                    "The method " + method + " belongs to the " + method.getService() + " service, not to the " + service
                                               + " service");
        }

        // after configuring endpoint then get or create xchange instance
        XChange xchange = getOrCreateXChange(remaining, configuration.getApiKey(), configuration.getSecretKey());
        endpoint.setXchange(xchange);

        // parse the currency pair once, after the exchange is created, as it may register the currencies it trades:
        // like the currency option, a currency that XChange does not know fails here, not with each message
        configuration.getAsCurrencyPair();
        return endpoint;
    }

    public XChange getXChange(String name) {
        return xchanges.get(name);
    }

    void putXChange(String name, XChange xchange) {
        xchanges.put(name, xchange);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        xchanges.clear();
        xchangesWithKeys.clear();
    }

    protected Exchange createExchange(Class<? extends Exchange> exchangeClass) {
        return ExchangeFactory.INSTANCE.createExchange(exchangeClass);
    }

    /**
     * Creates the exchange with the default specification of its XChange module and the given API keys, which are only
     * set when not null.
     */
    protected Exchange createExchange(Class<? extends Exchange> exchangeClass, String apiKey, String secretKey) {
        return ExchangeFactory.INSTANCE.createExchange(exchangeClass, apiKey, secretKey);
    }

    private XChange getOrCreateXChange(String name, String apiKey, String secretKey) {
        if (apiKey == null && secretKey == null) {
            return xchanges.computeIfAbsent(name, xc -> new XChange(createExchange(loadExchangeClass(name))));
        }
        return xchangesWithKeys.computeIfAbsent(new Credentials(name, apiKey, secretKey),
                c -> new XChange(createExchange(loadExchangeClass(name), apiKey, secretKey)));
    }

    private Class<? extends Exchange> loadExchangeClass(String name) {
        Class<? extends Exchange> exchangeClass = XChangeHelper.loadXChangeClass(getCamelContext(), name);
        Assert.notNull(exchangeClass, "XChange not supported: " + name);
        return exchangeClass;
    }

    private record Credentials(String name, String apiKey, String secretKey) {
        @Override
        public String toString() {
            // never show the keys
            return name;
        }
    }

}
