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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.spi.AggregationRepository;
import org.fusesource.hawtdb.api.Index;
import org.fusesource.hawtdb.api.Transaction;
import org.fusesource.hawtdb.util.buffer.Buffer;
import org.fusesource.hawtdb.util.buffer.DataByteArrayInputStream;
import org.fusesource.hawtdb.util.buffer.DataByteArrayOutputStream;
import org.fusesource.hawtdb.util.marshaller.Marshaller;
import org.fusesource.hawtdb.util.marshaller.ObjectMarshaller;

/**
 * An instance of AggregationRepository which is backed by a HawtDB.
 */
public class HawtDBAggregationRepository<K> implements AggregationRepository<K> {

    private HawtDBFile file;
    private String name;
    private Marshaller<K> keyMarshaller = new ObjectMarshaller<K>();
    private Marshaller<DefaultExchangeHolder> exchangeMarshaller = new ObjectMarshaller<DefaultExchangeHolder>();

    public Exchange add(CamelContext camelContext, K key, Exchange exchange) {
        try {
            // If we could guarantee that the key and exchange are immutable,
            // then we could have stuck them directly into the index, 
            // HawtDB could then eliminate the need to marshal and un-marshal  
            // in some cases.  But since we can.. we are going to force
            // early marshaling.
            final Buffer keyBuffer = marshallKey(key);
            final Buffer exchangeBuffer = marshallExchange(camelContext, exchange);
            Buffer rc = file.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = file.getRepositoryIndex(tx, name);
                    return index.put(keyBuffer, exchangeBuffer);
                }
            });
            if (rc == null) {
                return null;
            }
            // TODO: We can improve performance by not returning the old when adding
            return unmarshallExchange(camelContext, rc);
        } catch (IOException e) {
            throw new RuntimeException("Error adding to repository " + name + " with key " + key, e);
        }
    }


    public Exchange get(CamelContext camelContext, K key) {
        try {
            final Buffer keyBuffer = marshallKey(key);
            Buffer rc = file.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = file.getRepositoryIndex(tx, name);
                    return index.get(keyBuffer);
                }
            });
            if (rc == null) {
                return null;
            }
            return unmarshallExchange(camelContext, rc);
        } catch (IOException e) {
            throw new RuntimeException("Error getting key " + key + " from repository " + name, e);
        }
    }

    public void remove(CamelContext camelContext, K key) {
        try {
            final Buffer keyBuffer = marshallKey(key);
            file.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = file.getRepositoryIndex(tx, name);
                    return index.remove(keyBuffer);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error removing key " + key + " from repository " + name, e);
        }
    }

    protected Buffer marshallKey(K key) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        keyMarshaller.writePayload(key, baos);
        return baos.toBuffer();
    }

    protected Buffer marshallExchange(CamelContext camelContext, Exchange exchange) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false);
        // add the aggregated size property as the only property we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE, exchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));
        exchangeMarshaller.writePayload(pe, baos);
        return baos.toBuffer();
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);
        DefaultExchangeHolder pe = exchangeMarshaller.readPayload(bais);
        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);
        return answer;
    }

    public HawtDBFile getFile() {
        return file;
    }

    public void setFile(HawtDBFile file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
