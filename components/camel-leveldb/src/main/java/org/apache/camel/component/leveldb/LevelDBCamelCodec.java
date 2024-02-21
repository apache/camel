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
import org.apache.camel.component.leveldb.serializer.DefaultLevelDBSerializer;

public final class LevelDBCamelCodec {

    private final LevelDBSerializer serializer;

    public LevelDBCamelCodec(LevelDBSerializer serializer) {
        if (serializer == null) {
            this.serializer = new DefaultLevelDBSerializer();
        } else {
            this.serializer = serializer;
        }
    }

    public byte[] marshallKey(String key) throws IOException {
        return serializer.serializeKey(key);
    }

    public String unmarshallKey(byte[] buffer) throws IOException {
        return serializer.deserializeKey(buffer);
    }

    public byte[] marshallExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders)
            throws IOException {

        return serializer.serializeExchange(camelContext, exchange, allowSerializedHeaders);
    }

    public Exchange unmarshallExchange(CamelContext camelContext, byte[] buffer) throws IOException {
        Exchange answer = serializer.deserializeExchange(camelContext, buffer);

        // restore the from endpoint
        String fromEndpointUri = (String) answer.removeProperty("CamelAggregatedFromEndpoint");
        if (fromEndpointUri != null) {
            Endpoint fromEndpoint = camelContext.hasEndpoint(fromEndpointUri);
            if (fromEndpoint != null) {
                answer.getExchangeExtension().setFromEndpoint(fromEndpoint);
            }
        }
        return answer;
    }
}
