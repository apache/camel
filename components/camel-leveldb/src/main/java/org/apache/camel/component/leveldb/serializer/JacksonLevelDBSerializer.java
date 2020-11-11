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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.leveldb.serializer.jackson.ObjectMapperHelper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;

public class JacksonLevelDBSerializer extends AbstractLevelDBSerializer {

    private final ObjectMapper objectMapper;

    public JacksonLevelDBSerializer() {
        this(null);
    }

    public JacksonLevelDBSerializer(Module customMudule) {
        this.objectMapper = ObjectMapperHelper.create(customMudule);
    }

    @Override
    public byte[] serializeKey(String key) throws IOException {
        return objectMapper.writeValueAsBytes(key);
    }

    @Override
    public String deserializeKey(byte[] buffer) throws IOException {
        return objectMapper.readValue(buffer, String.class);
    }

    @Override
    public byte[] serializeExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders)
            throws IOException {
        Object inBody = exchange.getIn().getBody();
        Object outBody = null;
        if (exchange.getMessage() != null) {
            outBody = exchange.getMessage().getBody();
        }

        DefaultExchangeHolder pe = createExchangeHolder(exchange, allowSerializedHeaders);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(baos)) {
            serializeByteArrayBody(inBody, dos);
            serializeByteArrayBody(outBody, dos);
            objectMapper.writeValue(baos, pe);
            return baos.toByteArray();
        }
    }

    @Override
    public Exchange deserializeExchange(CamelContext camelContext, byte[] buffer) throws IOException {
        Object inBody;
        Object outBody;
        DefaultExchangeHolder pe;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(buffer); DataInputStream dis = new DataInputStream(bis)) {
            inBody = deserializeByteArrayBody(dis);
            outBody = deserializeByteArrayBody(dis);
            pe = objectMapper.readValue(bis, DefaultExchangeHolder.class);
        }

        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);

        if (inBody != null) {
            answer.getIn().setBody(inBody);
        }
        if (outBody != null) {
            answer.getMessage().setBody(outBody);
        }
        return answer;
    }

    private void serializeByteArrayBody(Object body, DataOutputStream dos) throws IOException {
        if (body instanceof byte[]) {
            int length = ((byte[]) body).length;
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(length);
            dos.write(bb.array());
            dos.write((byte[]) body);
        } else {
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(0);
            dos.write(bb.array());
        }
    }

    private Object deserializeByteArrayBody(DataInputStream dis) throws IOException {
        byte[] b = new byte[4];
        dis.read(b);
        int length = ByteBuffer.wrap(b).getInt();
        byte[] payload = null;
        if (length > 0) {
            payload = new byte[length];
            dis.read(payload);
        }

        return payload;
    }

}
