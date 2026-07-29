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
package org.apache.camel.dataformat.tarfile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import static org.apache.camel.Exchange.FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The TAR entry name is attacker-influenced archive content, so when it is promoted to the CamelFileName control header
 * it must be reduced to a leaf name (no path segments). See CAMEL-24293.
 */
class TarFileNameStripPathTest extends CamelTestSupport {

    private static byte[] tarWithEntry(String entryName) throws Exception {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos)) {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(payload.length);
            tos.putArchiveEntry(entry);
            tos.write(payload);
            tos.closeArchiveEntry();
        }
        return bos.toByteArray();
    }

    @Test
    void entryNameWithPathIsStrippedToLeaf() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", tarWithEntry("subdir/evil.txt"));

        mock.assertIsSatisfied();
        assertEquals("evil.txt", mock.getReceivedExchanges().get(0).getIn().getHeader(FILE_NAME, String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").unmarshal(new TarFileDataFormat()).to("mock:result");
            }
        };
    }
}
