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
package org.apache.camel.component.infinispan.remote.protostream;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import com.example.external.NotAllowedSerializable;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultExchangeHolderUtilsTest {

    @Test
    public void testDeserializeAcceptsDefaultExchangeHolder() {
        DefaultCamelContext context = new DefaultCamelContext();
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");

        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true);
        byte[] bytes = DefaultExchangeHolderUtils.serialize(holder);

        DefaultExchangeHolder roundTripped = DefaultExchangeHolderUtils.deserialize(bytes);
        assertNotNull(roundTripped);

        DefaultExchange restored = new DefaultExchange(context);
        DefaultExchangeHolder.unmarshal(restored, roundTripped);
        assertEquals("hello", restored.getIn().getBody());
    }

    @Test
    public void testDeserializeRejectsUnlistedType() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(new NotAllowedSerializable("blocked"));
        }

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> DefaultExchangeHolderUtils.deserialize(baos.toByteArray()));
        Throwable cause = thrown.getCause();
        assertNotNull(cause);
    }
}
