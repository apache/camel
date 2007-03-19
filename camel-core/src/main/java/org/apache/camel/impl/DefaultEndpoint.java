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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeConverter;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision$
 */
public abstract class DefaultEndpoint<E> implements Endpoint<E> {
    private String endpointUri;
    private ExchangeConverter exchangeConverter;

    protected DefaultEndpoint(String endpointUri, ExchangeConverter exchangeConverter) {
        this.endpointUri = endpointUri;
        this.exchangeConverter = exchangeConverter;
    }

    public int hashCode() {
        return endpointUri.hashCode() * 37 + 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DefaultEndpoint) {
            DefaultEndpoint that = (DefaultEndpoint) object;
            return ObjectHelper.equals(this.endpointUri, that.endpointUri);
        }
        return false;
    }

    @Override
    public String toString() {
        return "Endpoint[" + endpointUri  + "]";
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public ExchangeConverter getExchangeConverter() {
        return exchangeConverter;
    }

    /**
     * Converts the given exchange to the specified exchange type
     */
    public E convertTo(Class<E> type, Exchange exchange) {
        // TODO we could infer type parameter
        if (type.isInstance(exchange)) {
            return type.cast(exchange);
        }
        return getExchangeConverter().convertTo(type, exchange);
    }

}
