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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerFileWithCharsetTest extends FtpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FtpProducerFileWithCharsetTest.class);

    private String payload = "\u00e6\u00f8\u00e5 \u00a9";

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/upload?charset=iso-8859-1&password=admin";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        byte[] utf = payload.getBytes("utf-8");
        byte[] iso = payload.getBytes("iso-8859-1");

        LOG.debug("utf: {}", new String(utf, Charset.forName("utf-8")));
        LOG.debug("iso: {}", new String(iso, Charset.forName("iso-8859-1")));

        for (byte b : utf) {
            LOG.debug("utf byte: {}", b);
        }
        for (byte b : iso) {
            LOG.debug("iso byte: {}", b);
        }
        super.setUp();
    }

    @Test
    public void testProducerWithCharset() throws Exception {
        sendFile(getFtpUrl(), payload, "charset/iso.txt");

        File file = new File(FTP_ROOT_DIR + "/upload/charset/iso.txt");
        assertTrue(file.exists(), "The uploaded file should exists");
        String fileContent = new String(IOConverter.toByteArray(file), "iso-8859-1");
        assertEquals(fileContent, payload);

        // Lets also test byte wise
        InputStream fis = IOHelper.buffered(new FileInputStream(file));
        byte[] buffer = new byte[100];

        int len = fis.read(buffer);
        assertTrue(len != -1, "Should read data: " + len);
        byte[] data = new byte[len];
        System.arraycopy(buffer, 0, data, 0, len);
        fis.close();

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
