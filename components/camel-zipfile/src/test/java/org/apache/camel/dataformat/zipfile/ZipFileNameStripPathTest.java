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
package org.apache.camel.dataformat.zipfile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.Exchange.FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ZIP entry name is attacker-influenced archive content, so when it is promoted to the CamelFileName control header
 * it must be reduced to a leaf name (no path segments) - for both the data format {@code unmarshal} and the
 * iterator/splitter ({@link ZipSplitter}) modes. See CAMEL-24293.
 */
class ZipFileNameStripPathTest extends CamelTestSupport {

    private static byte[] zipWithEntry(String entryName) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write("payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    @Test
    void entryNameWithPathIsStrippedToLeaf() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", zipWithEntry("subdir/evil.txt"));

        mock.assertIsSatisfied();
        assertEquals("evil.txt", mock.getReceivedExchanges().get(0).getIn().getHeader(FILE_NAME, String.class));
    }

    @Test
    void iteratorEntryNameWithPathIsStrippedToLeaf() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:iterated");
        mock.expectedMessageCount(1);

        template.sendBody("direct:iterate", zipWithEntry("subdir/evil.txt"));

        mock.assertIsSatisfied();
        // the splitter path reduces CamelFileName to the leaf name too ...
        assertEquals("evil.txt",
                mock.getReceivedExchanges().get(0).getIn().getHeader(FILE_NAME, String.class));
        // ... while the full entry name stays available on the dedicated header
        assertEquals("subdir/evil.txt",
                mock.getReceivedExchanges().get(0).getIn().getHeader("zipFileName", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").unmarshal(new ZipFileDataFormat()).to("mock:result");
                from("direct:iterate").split(new ZipSplitter()).streaming().to("mock:iterated");
            }
        };
    }
}
