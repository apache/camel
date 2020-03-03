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
package org.apache.camel.component.leveldb;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtbuf.codec.Codec;
import org.fusesource.hawtbuf.codec.ObjectCodec;
import org.fusesource.hawtbuf.codec.StringCodec;

public final class LevelDBCamelCodec {

    private Codec<String> keyCodec = new StringCodec();
    private Codec<DefaultExchangeHolder> exchangeCodec = new ObjectCodec<>();

    public Buffer marshallKey(String key) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        keyCodec.encode(key, baos);
        return baos.toBuffer();
    }

    public String unmarshallKey(Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);
        String key = keyCodec.decode(bais);
        return key;
    }

    public Buffer marshallExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false, allowSerializedHeaders);
        // add the aggregated size and timeout property as the only properties we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE, exchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_TIMEOUT, exchange.getProperty(Exchange.AGGREGATED_TIMEOUT, Long.class));
        // add the aggregated completed by property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COMPLETED_BY, exchange.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));
        // add the aggregated correlation key property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_CORRELATION_KEY, exchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, String.class));
        // and a guard property if using the flexible toolbox aggregator
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COLLECTION_GUARD, exchange.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD, String.class));
        // persist the from endpoint as well
        if (exchange.getFromEndpoint() != null) {
            DefaultExchangeHolder.addProperty(pe, "CamelAggregatedFromEndpoint", exchange.getFromEndpoint().getEndpointUri());
        }
        exchangeCodec.encode(pe, baos);
        return baos.toBuffer();
    }

    public Exchange unmarshallExchange(CamelContext camelContext, Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);
        DefaultExchangeHolder pe = exchangeCodec.decode(bais);
        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);
        // restore the from endpoint
        String fromEndpointUri = (String) answer.removeProperty("CamelAggregatedFromEndpoint");
        if (fromEndpointUri != null) {
            Endpoint fromEndpoint = camelContext.hasEndpoint(fromEndpointUri);
            if (fromEndpoint != null) {
                answer.adapt(ExtendedExchange.class).setFromEndpoint(fromEndpoint);
            }
        }
        return answer;
    }

}
