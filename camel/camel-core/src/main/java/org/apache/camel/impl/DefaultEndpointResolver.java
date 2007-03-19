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
package org.apache.camel.impl;

import org.apache.camel.EndpointResolver;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangeConverter;
import org.apache.camel.seda.SedaEndpoint;

/**
 * A default implementation of {@link org.apache.camel.EndpointResolver}
 *
 * @version $Revision$
 */
public class DefaultEndpointResolver<E> implements EndpointResolver<E> {
    private ExchangeConverter exchangeConverter;

    public DefaultEndpointResolver() {
    }

    public DefaultEndpointResolver(ExchangeConverter exchangeConverter) {
        this.exchangeConverter = exchangeConverter;
    }
    public Endpoint<E> resolve(String uri) {
        // TODO we may want to cache them?
        return new SedaEndpoint<E>(uri, getExchangeConverter());
    }

    public ExchangeConverter getExchangeConverter() {
        if (exchangeConverter == null) {
            exchangeConverter =           createExchangeConverter();
        }
        return exchangeConverter;
    }

    public void setExchangeConverter(ExchangeConverter exchangeConverter) {
        this.exchangeConverter = exchangeConverter;
    }

    /**
     * Lazily create a default exchange converter implementation
     */
    protected ExchangeConverter createExchangeConverter() {
        return new DefaultExchangeConverter();
    }

}
