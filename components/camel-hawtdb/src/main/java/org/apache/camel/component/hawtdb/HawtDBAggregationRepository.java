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

    public Exchange add(K key, Exchange exchange) {
        try {
            // If we could guarantee that the key and exchange are immutable,
            // then we could have stuck them directly into the index, 
            // HawtDB could then eliminate the need to marshal and un-marshal  
            // in some cases.  But since we can.. we are going to force
            // early marshaling.
            final Buffer keyBuffer = marshallKey(key);
            final Buffer exchangeBuffer = marshallExchange(exchange);
            Buffer rc = file.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = file.getRepositoryIndex(tx, name);
                    return index.put(keyBuffer, exchangeBuffer);
                }
            });
            if (rc == null) {
                return null;
            }
            return unmarshallExchange(rc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Exchange get(K key) {
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
            return unmarshallExchange(rc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void remove(K key) {
        try {
            final Buffer keyBuffer = marshallKey(key);
            file.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = file.getRepositoryIndex(tx, name);
                    return index.remove(keyBuffer);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Buffer marshallKey(K key) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        keyMarshaller.writePayload(key, baos);
        return baos.toBuffer();
    }

    protected Buffer marshallExchange(Exchange exchange) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false);
        exchangeMarshaller.writePayload(pe, baos);
        return baos.toBuffer();
    }

    protected Exchange unmarshallExchange(Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);

        DefaultExchangeHolder pe = exchangeMarshaller.readPayload(bais);

        // create a new dummy default exchange which the aggregator must
        // set the CamelContext
        Exchange answer = new DefaultExchange((CamelContext) null);
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
