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
package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CamelURIParserTest {

    @Test
    public void testParseUri() throws Exception {
        String[] out1 = CamelURIParser.parseUri("smtp://localhost?username=davsclaus&password=secret");
        assertEquals("smtp", out1[0]);
        assertEquals("localhost", out1[1]);
        assertEquals("username=davsclaus&password=secret", out1[2]);
    }

    @Test
    public void testParseNoSlashUri() throws Exception {
        String[] out1 = CamelURIParser.parseUri("direct:start");
        assertEquals("direct", out1[0]);
        assertEquals("start", out1[1]);
        assertNull(out1[2]);
    }

    @Test
    public void testParseUriSlashAndQuery() throws Exception {
        String[] out1 = CamelURIParser.parseUri("file:/absolute?recursive=true");
        assertEquals("file", out1[0]);
        assertEquals("/absolute", out1[1]);
        assertEquals("recursive=true", out1[2]);

        String[] out2 = CamelURIParser.parseUri("file:///absolute?recursive=true");
        assertEquals("file", out2[0]);
        assertEquals("/absolute", out2[1]);
        assertEquals("recursive=true", out2[2]);

        String[] out3 = CamelURIParser.parseUri("file://relative?recursive=true");
        assertEquals("file", out3[0]);
        assertEquals("relative", out3[1]);
        assertEquals("recursive=true", out3[2]);

        String[] out4 = CamelURIParser.parseUri("file:relative?recursive=true");
        assertEquals("file", out4[0]);
        assertEquals("relative", out4[1]);
        assertEquals("recursive=true", out4[2]);
    }

    @Test
    public void testParseUriSlash() throws Exception {
        String[] out1 = CamelURIParser.parseUri("file:/absolute");
        assertEquals("file", out1[0]);
        assertEquals("/absolute", out1[1]);
        assertNull(out1[2]);

        String[] out2 = CamelURIParser.parseUri("file:///absolute");
        assertEquals("file", out2[0]);
        assertEquals("/absolute", out2[1]);
        assertNull(out2[2]);

        String[] out3 = CamelURIParser.parseUri("file://relative");
        assertEquals("file", out3[0]);
        assertEquals("relative", out3[1]);
        assertNull(out3[2]);

        String[] out4 = CamelURIParser.parseUri("file:relative");
        assertEquals("file", out4[0]);
        assertEquals("relative", out4[1]);
        assertNull(out4[2]);
    }

    @Test
    public void testParseInvalid() throws Exception {
        assertNull(CamelURIParser.parseUri("doesnotexists"));
        assertNull(CamelURIParser.parseUri("doesnotexists:"));
        assertNull(CamelURIParser.parseUri("doesnotexists/foo"));
        assertNull(CamelURIParser.parseUri("doesnotexists?"));
    }

    @Test
    public void testParseNoPathButSlash() throws Exception {
        String[] out1 = CamelURIParser.parseUri("file:/");
        assertEquals("file", out1[0]);
        assertEquals("/", out1[1]);
        assertNull(out1[2]);

        String[] out2 = CamelURIParser.parseUri("file:///");
        assertEquals("file", out2[0]);
        assertEquals("/", out2[1]);
        assertNull(out2[2]);
    }

    @Test
    public void testParseEmptyQuery() throws Exception {
        String[] out1 = CamelURIParser.parseUri("file:relative");
        assertEquals("file", out1[0]);
        assertEquals("relative", out1[1]);
        assertNull(out1[2]);

        String[] out2 = CamelURIParser.parseUri("file:relative?");
        assertEquals("file", out2[0]);
        assertEquals("relative", out2[1]);
        assertNull(out2[2]);
    }

    @Test
    public void testFastParse() throws Exception {
        String[] out1 = CamelURIParser.fastParseUri("file:relative");
        assertEquals("file", out1[0]);
        assertEquals("relative", out1[1]);
        assertNull(out1[2]);

        String[] out2 = CamelURIParser.fastParseUri("file://relative");
        assertEquals(CamelURIParser.URI_ALREADY_NORMALIZED, out2);

        String[] out3 = CamelURIParser.fastParseUri("file:relative?delete=true");
        assertEquals("file", out3[0]);
        assertEquals("relative", out3[1]);
        assertEquals("delete=true", out3[2]);

        String[] out4 = CamelURIParser.fastParseUri("file://relative?delete=true");
        assertEquals("file", out4[0]);
        assertEquals("relative", out4[1]);
        assertEquals("delete=true", out4[2]);
    }

}
