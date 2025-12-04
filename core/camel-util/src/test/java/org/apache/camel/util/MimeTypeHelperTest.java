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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MimeTypeHelperTest {

    @Test
    void testSanitizeMimeTypes() {
        assertNull(MimeTypeHelper.sanitizeMimeType(null));
        assertEquals("application/json", MimeTypeHelper.sanitizeMimeType("application/json"));
        assertEquals("application/xslt+xml", MimeTypeHelper.sanitizeMimeType("application/xslt xml"));
        assertEquals(
                "application/xslt+xml,application/xml",
                MimeTypeHelper.sanitizeMimeType("application/xslt xml , application/xml"));
    }

    @Test
    void testMimeType() {
        assertEquals("application/xslt+xml", MimeTypeHelper.probeMimeType("xslt"));
        assertEquals("application/json", MimeTypeHelper.probeMimeType("json"));
        assertEquals("application/xml", MimeTypeHelper.probeMimeType("xml"));
        assertEquals("text/plain", MimeTypeHelper.probeMimeType("txt"));
        assertEquals("application/metalink4+xml", MimeTypeHelper.probeMimeType("meta4"));

        assertEquals("application/xslt+xml", MimeTypeHelper.probeMimeType("foo.xslt"));
        assertEquals("application/json", MimeTypeHelper.probeMimeType("foo.json"));
        assertEquals("application/xml", MimeTypeHelper.probeMimeType("foo.xml"));
        assertEquals("text/plain", MimeTypeHelper.probeMimeType("foo.txt"));
        assertEquals("application/metalink4+xml", MimeTypeHelper.probeMimeType("foo.meta4"));

        assertEquals("application/xslt+xml", MimeTypeHelper.probeMimeType("foo.bar.xslt"));
        assertEquals("application/json", MimeTypeHelper.probeMimeType("foo.bar.json"));
        assertEquals("application/xml", MimeTypeHelper.probeMimeType("foo.bar.xml"));
        assertEquals("text/plain", MimeTypeHelper.probeMimeType("foo.bar.txt"));
        assertEquals("application/metalink4+xml", MimeTypeHelper.probeMimeType("foo.meta4"));

        assertEquals("application/xslt+xml", MimeTypeHelper.probeMimeType("FOO.BAR.XSLT"));
        assertEquals("application/json", MimeTypeHelper.probeMimeType("FOO.BAR.JSON"));
        assertEquals("application/xml", MimeTypeHelper.probeMimeType("foo.BAR.XmL"));
        assertEquals("text/plain", MimeTypeHelper.probeMimeType("foo.bAr.TxT"));
        assertEquals("application/metalink4+xml", MimeTypeHelper.probeMimeType("foo.meta4"));

        // extra
        assertEquals("text/yaml", MimeTypeHelper.probeMimeType("yaml"));
        assertEquals("text/yaml", MimeTypeHelper.probeMimeType("yml"));

        assertNull(null, MimeTypeHelper.probeMimeType("unknown"));
    }
}
