/**
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
package org.apache.camel.processor.aggregate.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.util.IOHelper;

/**
 * Adapted from HawtDBCamelCodec
 */
public class JdbcCamelCodec {

    public byte[] marshallExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders) throws IOException {
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false, allowSerializedHeaders);
        // add the aggregated size and timeout property as the only properties we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE, exchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_TIMEOUT, exchange.getProperty(Exchange.AGGREGATED_TIMEOUT, Long.class));
        // add the aggregated completed by property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COMPLETED_BY, exchange.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));
        // add the aggregated correlation key property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_CORRELATION_KEY, exchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, String.class));
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_CORRELATION_KEY, exchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, String.class));
        // and a guard property if using the flexible toolbox aggregator
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COLLECTION_GUARD, exchange.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD, String.class));
        // persist the from endpoint as well
        if (exchange.getFromEndpoint() != null) {
            DefaultExchangeHolder.addProperty(pe, "CamelAggregatedFromEndpoint", exchange.getFromEndpoint().getEndpointUri());
        }
        return encode(pe);
    }

    public Exchange unmarshallExchange(CamelContext camelContext, byte[] buffer) throws IOException, ClassNotFoundException {
        DefaultExchangeHolder pe = decode(camelContext, buffer);
        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);
        // restore the from endpoint
        String fromEndpointUri = (String) answer.removeProperty("CamelAggregatedFromEndpoint");
        if (fromEndpointUri != null) {
            Endpoint fromEndpoint = camelContext.hasEndpoint(fromEndpointUri);
            if (fromEndpoint != null) {
                answer.setFromEndpoint(fromEndpoint);
            }
        }
        return answer;
    }

    private byte[] encode(Object object) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(bytesOut);
        objectOut.writeObject(object);
        objectOut.close();
        byte[] data = bytesOut.toByteArray();
        return data;
    }

    private DefaultExchangeHolder decode(CamelContext camelContext, byte[] dataIn) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(dataIn);

        ObjectInputStream objectIn = null;
        Object obj = null;
        try {
            objectIn = new ClassLoadingAwareObjectInputStream(camelContext, bytesIn);
            obj = objectIn.readObject();
        } finally {
            IOHelper.close(objectIn);
        }

        return (DefaultExchangeHolder)obj;
    }

}

