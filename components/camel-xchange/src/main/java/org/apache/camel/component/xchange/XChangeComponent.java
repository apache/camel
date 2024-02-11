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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.utils.Assert;

@Component("xchange")
public class XChangeComponent extends DefaultComponent {

    private final Map<String, XChange> xchanges = new HashMap<>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        XChangeConfiguration configuration = new XChangeConfiguration();
        configuration.setName(remaining);

        XChangeEndpoint endpoint = new XChangeEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        // after configuring endpoint then get or create xchange instance
        XChange xchange = getOrCreateXChange(remaining);
        endpoint.setXchange(xchange);

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
    }

    protected Exchange createExchange(Class<? extends Exchange> exchangeClass) {
        return ExchangeFactory.INSTANCE.createExchange(exchangeClass);
    }

    private synchronized XChange getOrCreateXChange(String name) {
        return xchanges.computeIfAbsent(name, xc -> {
            Class<? extends Exchange> exchangeClass = XChangeHelper.loadXChangeClass(getCamelContext(), name);
            Assert.notNull(exchangeClass, "XChange not supported: " + name);
            return new XChange(createExchange(exchangeClass));
        });
    }

}
