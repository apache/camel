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
package org.apache.camel.component.file;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class FileProducerCharsetUTFOptimizedTest extends ContextTestSupport {

    private byte[] utf;

    @Override
    @Before
    public void setUp() throws Exception {
        // use utf-8 as original payload with 00e6 which is a danish ae letter
        utf = "ABC\u00e6".getBytes("utf-8");

        deleteDirectory("target/data/charset");
        createDirectory("target/data/charset/input");

        log.debug("utf: {}", new String(utf, Charset.forName("utf-8")));

        for (byte b : utf) {
            log.debug("utf byte: {}", b);
        }

        // write the byte array to a file using plain API
        OutputStream fos = Files.newOutputStream(Paths.get("target/data/charset/input/input.txt"));
        fos.write(utf);
        fos.close();

        super.setUp();
    }

    @Test
    public void testFileProducerCharsetUTFOptimized() throws Exception {
        oneExchangeDone.matchesMockWaitTime();

        File file = new File("target/data/charset/output.txt");
        assertTrue("File should exist", file.exists());

        InputStream fis = Files.newInputStream(Paths.get(file.getAbsolutePath()));
        byte[] buffer = new byte[100];

        int len = fis.read(buffer);
        assertTrue("Should read data: " + len, len != -1);
        byte[] data = new byte[len];
        System.arraycopy(buffer, 0, data, 0, len);
        fis.close();

        // data should be in utf, where the danish ae is -61 -90
        assertEquals(5, data.length);
        assertEquals(65, data[0]);
        assertEquals(66, data[1]);
        assertEquals(67, data[2]);
        assertEquals(-61, data[3]);
        assertEquals(-90, data[4]);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/charset/input?initialDelay=0&delay=10&noop=true")
                    // no charset so its optimized to write directly
                    .to("file:target/data/charset/?fileName=output.txt");
            }
        };
    }
}
