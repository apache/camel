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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchangeHolder;

public class DefaultLevelDBSerializer extends AbstractLevelDBSerializer {

    @Override
    public byte[] serializeKey(String key) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(key);
            return baos.toByteArray();
        }
    }

    @Override
    public String deserializeKey(byte[] buffer) throws IOException {
        try (final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buffer))) {
            return (String) ois.readObject();
        } catch (ClassNotFoundException e) {
            //this should not happen because serialized content should be String
            throw new IllegalStateException("Content has to be serialized String.", e);
        }
    }

    @Override
    public byte[] serializeExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders)
            throws IOException {
        return serializeExchange(exchange, allowSerializedHeaders, h -> {
            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(h);
                return baos.toByteArray();
            }
        });
    }

    @Override
    public Exchange deserializeExchange(CamelContext camelContext, byte[] buffer) throws IOException {
        return deserializeExchange(camelContext, buffer, b -> {
            try (final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buffer))) {
                return (DefaultExchangeHolder) ois.readObject();
            } catch (ClassNotFoundException e) {
                //this should not happen because serialized content should be byte[]
                throw new IllegalStateException("Content has to be serialized String.", e);
            }
        });
    }
}
