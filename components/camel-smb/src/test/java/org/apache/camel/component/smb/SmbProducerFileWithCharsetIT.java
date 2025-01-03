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
package org.apache.camel.component.smb;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmbProducerFileWithCharsetIT extends SmbServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SmbProducerFileWithCharsetIT.class);

    private final String payload = "\u00e6\u00f8\u00e5 \u00a9";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/charset&charset=iso-8859-1",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPreSetup() {
        byte[] utf = payload.getBytes(StandardCharsets.UTF_8);
        byte[] iso = payload.getBytes(StandardCharsets.ISO_8859_1);

        LOG.debug("utf: {}", new String(utf, StandardCharsets.UTF_8));
        LOG.debug("iso: {}", new String(iso, StandardCharsets.ISO_8859_1));

        for (byte b : utf) {
            LOG.debug("utf byte: {}", b);
        }
        for (byte b : iso) {
            LOG.debug("iso byte: {}", b);
        }
    }

    @Test
    public void testProducerWithCharset() throws Exception {
        sendFile(getSmbUrl(), payload, "iso.txt");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(payload,
                        new String(copyFileContentFromContainer("/data/rw/charset/iso.txt"), StandardCharsets.ISO_8859_1)));

        byte[] data = copyFileContentFromContainer("/data/rw/charset/iso.txt");

        // data should be in iso, where the danish ae is -26, oe is -8 aa is -27
        // and copyright is -87
        assertEquals(5, data.length);
        assertEquals(-26, data[0]);
        assertEquals(-8, data[1]);
        assertEquals(-27, data[2]);
        assertEquals(32, data[3]);
        assertEquals(-87, data[4]);
    }
}
