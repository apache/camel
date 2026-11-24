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

class MimeTypeHelperTest {

    @Test
    void testSanitizeMimeTypes() {
        assertNull(MimeTypeHelper.sanitizeMimeType(null));
        assertEquals("application/json", MimeTypeHelper.sanitizeMimeType("application/json"));
        assertEquals("application/xslt+xml", MimeTypeHelper.sanitizeMimeType("application/xslt xml"));
        assertEquals("application/xslt+xml,application/xml",
                MimeTypeHelper.sanitizeMimeType("application/xslt xml , application/xml"));
    }

    @Test
    void testSanitizeMimeTypesWithParameters() {
        // spaces around ; and = in the parameters are removed, not turned into +
        assertEquals("application/json;q=0.5", MimeTypeHelper.sanitizeMimeType("application/json; q=0.5"));
        assertEquals("application/json;charset=utf-8", MimeTypeHelper.sanitizeMimeType("application/json ; charset = utf-8"));
        assertEquals("application/json;charset=utf-8;q=0.5",
                MimeTypeHelper.sanitizeMimeType("application/json; charset=utf-8; q=0.5"));
        assertEquals("application/json;q=0.5", MimeTypeHelper.sanitizeMimeType("application/json;q=0.5"));
        // the type/subtype token is still repaired
        assertEquals("application/xslt+xml;q=0.9", MimeTypeHelper.sanitizeMimeType("application/xslt xml; q=0.9"));
        assertEquals("application/vnd.api+json;ext=a+b", MimeTypeHelper.sanitizeMimeType("application/vnd.api json; ext=a b"));
        // several media types
        assertEquals("application/vnd.api+json,application/json;q=0.5",
                MimeTypeHelper.sanitizeMimeType("application/vnd.api+json, application/json; q=0.5"));
        assertEquals("application/xslt+xml,application/json;charset=utf-8,text/plain;q=0.1",
                MimeTypeHelper.sanitizeMimeType("application/xslt xml, application/json; charset=utf-8 , text/plain; q=0.1"));
        // empty parameters and media types are dropped
        assertEquals("application/json,text/plain", MimeTypeHelper.sanitizeMimeType("application/json;, ,text/plain"));
        assertEquals("", MimeTypeHelper.sanitizeMimeType(""));
    }

    @Test
    void testSanitizeMimeTypesWithQuotedParameters() {
        // a quoted value keeps its spaces, and a , or ; inside quotes does not split
        assertEquals("text/plain;foo=\"a b\"", MimeTypeHelper.sanitizeMimeType("text/plain; foo=\"a b\""));
        assertEquals("text/plain;foo=\"a, b; c\";q=0.5,application/json",
                MimeTypeHelper.sanitizeMimeType("text/plain; foo=\"a, b; c\"; q=0.5, application/json"));
        // an escaped quote inside the quoted value
        assertEquals("text/plain;foo=\"a \\\" b, c\",application/json",
                MimeTypeHelper.sanitizeMimeType("text/plain; foo=\"a \\\" b, c\", application/json"));
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
