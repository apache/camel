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
package org.apache.camel.component.hawtdb;

import java.io.IOException;
import java.io.Serializable;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.fusesource.hawtdb.util.buffer.Buffer;
import org.fusesource.hawtdb.util.buffer.DataByteArrayInputStream;
import org.fusesource.hawtdb.util.buffer.DataByteArrayOutputStream;
import org.fusesource.hawtdb.util.marshaller.Marshaller;
import org.fusesource.hawtdb.util.marshaller.ObjectMarshaller;
import org.fusesource.hawtdb.util.marshaller.StringMarshaller;

/**
 * @version $Revision$
 */
public final class HawtDBCamelMarshaller<K> {

    private Marshaller<K> keyMarshaller = new ObjectMarshaller<K>();
    private Marshaller<String> confirmKeyMarshaller = new StringMarshaller();
    private Marshaller<DefaultExchangeHolder> exchangeMarshaller = new ObjectMarshaller<DefaultExchangeHolder>();

    public Buffer marshallKey(K key) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        keyMarshaller.writePayload(key, baos);
        return baos.toBuffer();
    }

    public Buffer marshallConfirmKey(String exchangeId) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        confirmKeyMarshaller.writePayload(exchangeId, baos);
        return baos.toBuffer();
    }

    public Buffer marshallExchange(CamelContext camelContext, Exchange exchange) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false);
        // add the aggregated size property as the only property we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE, exchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));
        // add the aggregated completed by property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_COMPLETED_BY, exchange.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));
        // add the aggregated correlation key property to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_CORRELATION_KEY, exchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, Serializable.class));
        // persist the from endpoint as well
        if (exchange.getFromEndpoint() != null) {
            DefaultExchangeHolder.addProperty(pe, "CamelAggregatedFromEndpoint", exchange.getFromEndpoint().getEndpointUri());
        }
        exchangeMarshaller.writePayload(pe, baos);
        return baos.toBuffer();
    }

    public Exchange unmarshallExchange(CamelContext camelContext, Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);
        DefaultExchangeHolder pe = exchangeMarshaller.readPayload(bais);
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

}
