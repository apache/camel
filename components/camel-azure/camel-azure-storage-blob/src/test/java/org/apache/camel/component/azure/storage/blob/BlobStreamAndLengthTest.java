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
package org.apache.camel.component.azure.storage.blob;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlobStreamAndLengthTest extends CamelTestSupport {

    @Test
    void testPathBodyWithoutUploadSize(@TempDir Path dir) throws Exception {
        // bigger than the buffer of a BufferedInputStream, which a mark/reset based length probe cannot handle
        byte[] payload = new byte[1024 * 1024];
        new Random(42).nextBytes(payload);
        Path file = Files.write(dir.resolve("big.bin"), payload);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(file);

        BlobStreamAndLength blob = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);

        assertEquals(payload.length, blob.getStreamLength());
        try (InputStream is = blob.getInputStream()) {
            assertArrayEquals(payload, is.readAllBytes());
        }
    }
}
