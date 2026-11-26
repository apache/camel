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

import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-18685: the charsetErrorAction option of the file producer decides what happens with characters the charset
 * cannot encode.
 */
public class FileProducerCharsetErrorActionTest extends ContextTestSupport {

    // the euro sign has no ISO-8859-1 encoding
    private static final String BODY = "a\u20acb";

    @Test
    public void testReport() {
        Exception e = assertThrows(CamelExecutionException.class, () -> write("REPORT"));
        assertNotNull(ObjectHelper.getException(UnmappableCharacterException.class, e), e.getMessage());
    }

    @Test
    public void testDefaultIsReport() {
        Exception e = assertThrows(CamelExecutionException.class, () -> write(null));
        assertNotNull(ObjectHelper.getException(UnmappableCharacterException.class, e), e.getMessage());
    }

    @Test
    public void testReplace() throws Exception {
        write("REPLACE");
        assertArrayEquals(new byte[] { 'a', '?', 'b' }, Files.readAllBytes(testFile("out.txt")));
    }

    @Test
    public void testIgnore() throws Exception {
        write("IGNORE");
        assertArrayEquals(new byte[] { 'a', 'b' }, Files.readAllBytes(testFile("out.txt")));
    }

    @Test
    public void testIgnoreWithChecksum() throws Exception {
        // the checksum is computed from the bytes written
        Exchange out = template.request(fileUri("?charset=iso-8859-1&charsetErrorAction=IGNORE&checksumFileAlgorithm=md5"),
                e -> {
                    e.getIn().setBody(BODY);
                    e.getIn().setHeader(Exchange.FILE_NAME, "out.txt");
                });
        assertNull(out.getException());
        assertArrayEquals(new byte[] { 'a', 'b' }, Files.readAllBytes(testFile("out.txt")));
        // md5 of "ab"
        assertEquals("187ef4436122d1cc2f40dc2b92f0eba0", out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));
    }

    private void write(String action) {
        template.sendBodyAndHeader(
                fileUri("?charset=iso-8859-1" + (action != null ? "&charsetErrorAction=" + action : "")), BODY,
                Exchange.FILE_NAME, "out.txt");
    }
}
