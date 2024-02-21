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
package org.apache.camel.component.leveldb.serializer;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.component.leveldb.LevelDBSerializer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;

public abstract class AbstractLevelDBSerializer implements LevelDBSerializer {

    protected byte[] serializeExchange(
            Exchange exchange, boolean allowSerializedHeaders, Serializer serializer)
            throws IOException {
        DefaultExchangeHolder pe = createExchangeHolder(exchange, allowSerializedHeaders);
        return serializer.serialize(pe);
    }

    protected Exchange deserializeExchange(CamelContext camelContext, byte[] buffer, Deserializer deserializer)
            throws IOException {
        DefaultExchangeHolder pe = deserializer.deserialize(buffer);
        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);

        return answer;
    }

    DefaultExchangeHolder createExchangeHolder(Exchange exchange, boolean allowSerializedHeaders) {
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false, allowSerializedHeaders);
        // add the aggregated size and timeout property as the only properties we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE,
                exchange.getProperty(ExchangePropertyKey.AGGREGATED_SIZE, Integer.class));
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_TIMEOUT,
                exchange.getProperty(ExchangePropertyKey.AGGREGATED_TIMEOUT, Long.class));
        // add the aggregated completed by property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COMPLETED_BY,
                exchange.getProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, String.class));
        // add the aggregated correlation key property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_CORRELATION_KEY,
                exchange.getProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, String.class));
        // and a guard property if using the flexible toolbox aggregator
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COLLECTION_GUARD,
                exchange.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD, String.class));
        // persist the from endpoint as well
        if (exchange.getFromEndpoint() != null) {
            DefaultExchangeHolder.addProperty(pe, "CamelAggregatedFromEndpoint", exchange.getFromEndpoint().getEndpointUri());
        }
        return pe;
    }

    @FunctionalInterface
    protected interface Deserializer {
        DefaultExchangeHolder deserialize(byte[] buffer) throws IOException;
    }

    @FunctionalInterface
    protected interface Serializer {
        byte[] serialize(DefaultExchangeHolder holder) throws IOException;
    }

}
