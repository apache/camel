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
import static org.junit.jupiter.api.Assertions.assertSame;

public class EscapeHelperTest {

    @Test
    public void testHtml() {
        assertEquals("Monday &amp; Tuesday", EscapeHelper.html("Monday & Tuesday"));
        assertEquals("&lt;b&gt;bold&lt;/b&gt;", EscapeHelper.html("<b>bold</b>"));
        assertEquals("say &quot;hi&quot; &amp; &#39;bye&#39;", EscapeHelper.html("say \"hi\" & 'bye'"));
        assertEquals("caf\u00e9", EscapeHelper.html("caf\u00e9"));
    }

    @Test
    public void testXml() {
        assertEquals("Monday &amp; Tuesday", EscapeHelper.xml("Monday & Tuesday"));
        assertEquals("&lt;a href=&quot;x&quot;&gt;it&apos;s&lt;/a&gt;", EscapeHelper.xml("<a href=\"x\">it's</a>"));
    }

    @Test
    public void testJson() {
        assertEquals("say \\\"hi\\\"", EscapeHelper.json("say \"hi\""));
        assertEquals("c:\\\\temp", EscapeHelper.json("c:\\temp"));
        assertEquals("line1\\nline2\\ttab\\r\\b\\f", EscapeHelper.json("line1\nline2\ttab\r\b\f"));
        assertEquals("a\\u0001b", EscapeHelper.json("a\u0001b"));
        assertEquals("it's <b>", EscapeHelper.json("it's <b>"));
    }

    @Test
    public void testJs() {
        assertEquals("it\\'s \\\"quoted\\\"", EscapeHelper.js("it's \"quoted\""));
        assertEquals("<\\/script>", EscapeHelper.js("</script>"));
        assertEquals("a\\nb", EscapeHelper.js("a\nb"));
        assertEquals("a/b", EscapeHelper.js("a/b"));
    }

    @Test
    public void testSql() {
        assertEquals("O''Reilly", EscapeHelper.sql("O'Reilly"));
        assertEquals("'''' ", EscapeHelper.sql("'' "));
        assertEquals("plain", EscapeHelper.sql("plain"));
    }

    @Test
    public void testUrl() {
        assertEquals("Camel%20in%20Action", EscapeHelper.url("Camel in Action"));
        assertEquals("a%26b%3Dc%2Fd%3Fe", EscapeHelper.url("a&b=c/d?e"));
        assertEquals("caf%C3%A9", EscapeHelper.url("caf\u00e9"));
        assertEquals("plain-text_1.2*", EscapeHelper.url("plain-text_1.2*"));
        assertEquals("a%2Bb", EscapeHelper.url("a+b")); // + in input -> %2B, not %20
    }

    @Test
    public void testNoChangeReturnsSameInstance() {
        String value = "nothing to escape";
        assertSame(value, EscapeHelper.html(value));
        assertSame(value, EscapeHelper.xml(value));
        assertSame(value, EscapeHelper.json(value));
        assertSame(value, EscapeHelper.js(value));
        assertSame(value, EscapeHelper.sql(value));
    }

    @Test
    public void testNull() {
        for (EscapeHelper.Kind kind : EscapeHelper.Kind.values()) {
            assertNull(EscapeHelper.escape(kind, null));
        }
    }

    @Test
    public void testKindFromName() {
        assertEquals(EscapeHelper.Kind.HTML, EscapeHelper.Kind.fromName("html"));
        assertEquals(EscapeHelper.Kind.XML, EscapeHelper.Kind.fromName(" XML "));
        assertEquals(EscapeHelper.Kind.JSON, EscapeHelper.Kind.fromName("Json"));
        assertEquals(EscapeHelper.Kind.JS, EscapeHelper.Kind.fromName("js"));
        assertEquals(EscapeHelper.Kind.JS, EscapeHelper.Kind.fromName("javascript"));
        assertEquals(EscapeHelper.Kind.SQL, EscapeHelper.Kind.fromName("sql"));
        assertEquals(EscapeHelper.Kind.URL, EscapeHelper.Kind.fromName("url"));
        assertNull(EscapeHelper.Kind.fromName("csv"));
        assertNull(EscapeHelper.Kind.fromName(null));
    }

    @Test
    public void testKindEscape() {
        assertEquals("a &amp; b", EscapeHelper.Kind.HTML.escape("a & b"));
        assertEquals("a%20%26%20b", EscapeHelper.Kind.URL.escape("a & b"));
    }
}
