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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpConsumerWithCharsetTest extends FtpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FtpConsumerWithCharsetTest.class);

    private final String payload = "\u00e6\u00f8\u00e5 \u00a9";

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/upload?password=admin&charset=iso-8859-1";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        byte[] iso = payload.getBytes("iso-8859-1");
        byte[] utf = payload.getBytes("utf-8");

        LOG.debug("iso: {}", new String(iso, Charset.forName("iso-8859-1")));
        LOG.debug("utf: {}", new String(utf, Charset.forName("utf-8")));

        for (byte b : iso) {
            LOG.debug("iso byte: {}", b);
        }

        for (byte b : utf) {
            LOG.debug("utf byte: {}", b);
        }

        prepareFtpServer();
        // Check that the payload exists in upload and is in iso charset.ß
        File file = new File(service.getFtpRootDir() + "/upload/iso.txt");
        assertTrue(file.exists(), "The uploaded file should exists");

        // Lets also test byte wise
        InputStream fis = IOHelper.buffered(new FileInputStream(file));
        byte[] buffer = new byte[100];

        int len = fis.read(buffer);
        assertNotEquals(-1, len, "Should read data: " + len);
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

    @Test
    public void testConsumerWithCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(payload);
        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        RemoteFile<?> file = (RemoteFile<?>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        assertNotNull(file);
        assertEquals("iso-8859-1", file.getCharset());
        // The String will be encoded with UTF-8 by default
        byte[] data = exchange.getIn().getBody(String.class).getBytes("UTF-8");
        // data should be in iso, where the danish ae is -61 -90, oe is -61 -72
        // aa is -61 -91
        // and copyright is -62 -87
        assertEquals(9, data.length);
        assertEquals(-61, data[0]);
        assertEquals(-90, data[1]);

        assertEquals(-61, data[2]);
        assertEquals(-72, data[3]);

        assertEquals(-61, data[4]);
        assertEquals(-91, data[5]);

        assertEquals(32, data[6]);

        assertEquals(-62, data[7]);
        assertEquals(-87, data[8]);
    }

    private void prepareFtpServer() throws Exception {
        sendFile(getFtpUrl(), payload, "iso.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
