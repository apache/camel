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
package org.apache.camel.component.azure.storage.datalake;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileStreamAndLengthTest extends CamelTestSupport {

    @Test
    void testStreamBodyWithoutMarkSupport() throws Exception {
        byte[] payload = new byte[64 * 1024];
        new Random(42).nextBytes(payload);

        // a stream that does not support mark/reset, such as a network stream
        InputStream stream = new FilterInputStream(new ByteArrayInputStream(payload)) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(stream);

        FileStreamAndLength file = FileStreamAndLength.createFileStreamAndLengthFromExchangeBody(exchange);

        assertEquals(payload.length, file.getStreamLength());
        try (InputStream is = file.getInputStream()) {
            assertArrayEquals(payload, is.readAllBytes());
        }
    }
}
