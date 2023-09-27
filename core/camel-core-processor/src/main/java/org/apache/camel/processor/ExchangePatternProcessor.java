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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;

/**
 * Processor to set {@link org.apache.camel.ExchangePattern} on the {@link org.apache.camel.Exchange}.
 */
public class ExchangePatternProcessor extends AsyncProcessorSupport implements IdAware, RouteIdAware {
    private String id;
    private String routeId;
    private ExchangePattern exchangePattern = ExchangePattern.InOnly;

    public ExchangePatternProcessor() {
    }

    public ExchangePatternProcessor(ExchangePattern ep) {
        setExchangePattern(ep);
    }

    public void setExchangePattern(ExchangePattern ep) {
        exchangePattern = ep;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.setPattern(exchangePattern);
        callback.done(true);
        return true;
    }

    @Override
    public String toString() {
        return id;
    }

}
