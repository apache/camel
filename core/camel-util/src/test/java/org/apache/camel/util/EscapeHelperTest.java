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

    @Test
    public void testUnescapeEntities() {
        assertEquals("Monday & Tuesday", EscapeHelper.unescapeEntities("Monday &amp; Tuesday"));
        assertEquals("<a href=\"x\">it's</a>",
                EscapeHelper.unescapeEntities("&lt;a href=&quot;x&quot;&gt;it&apos;s&lt;/a&gt;"));
        assertEquals("'''\u00e9\ud83d\ude00", EscapeHelper.unescapeEntities("&#39;&#x27;&#X27;&#233;&#x1F600;"));
        // decoded once: &amp;lt; is the text &lt;
        assertEquals("&lt;", EscapeHelper.unescapeEntities("&amp;lt;"));
        // not an entity of XML, or not a valid one: kept as it is
        assertEquals("&nbsp;&foo;&#;&#x;&#12a;&#x110000;& ;a & b;&amp", EscapeHelper.unescapeEntities(
                "&nbsp;&foo;&#;&#x;&#12a;&#x110000;& ;a & b;&amp"));
        assertEquals("&#\u0663;", EscapeHelper.unescapeEntities("&#\u0663;"));
        assertNull(EscapeHelper.unescapeEntities(null));
    }

    @Test
    public void testUnescapeJsonAndJs() {
        assertEquals("say \"hi\"", EscapeHelper.unescapeJson("say \\\"hi\\\""));
        assertEquals("c:\\temp", EscapeHelper.unescapeJson("c:\\\\temp"));
        assertEquals("line1\nline2\ttab\r\b\f/", EscapeHelper.unescapeJson("line1\\nline2\\ttab\\r\\b\\f\\/"));
        assertEquals("a\u0001b\u00e9", EscapeHelper.unescapeJson("a\\u0001b\\u00E9"));
        // not a valid escape: kept as it is
        assertEquals("\\x \\u12 \\u12zz \\'", EscapeHelper.unescapeJson("\\x \\u12 \\u12zz \\'"));
        assertEquals("end\\", EscapeHelper.unescapeJson("end\\"));

        assertEquals("it's </script>", EscapeHelper.unescapeJs("it\\'s <\\/script>"));
        assertNull(EscapeHelper.unescapeJson(null));
    }

    @Test
    public void testUnescapeSql() {
        assertEquals("O'Reilly", EscapeHelper.unescapeSql("O''Reilly"));
        assertEquals("''", EscapeHelper.unescapeSql("''''"));
        assertEquals("Camel", EscapeHelper.unescapeSql("Camel"));
    }

    @Test
    public void testUnescapeUrl() {
        assertEquals("Camel in Action?", EscapeHelper.unescapeUrl("Camel%20in%20Action%3F"));
        assertEquals("a b+c", EscapeHelper.unescapeUrl("a+b%2Bc"));
        assertEquals("caf\u00e9 \u6771\u4eac", EscapeHelper.unescapeUrl("caf%C3%A9%20%E6%9D%B1%E4%BA%AC"));
        // not a valid percent sequence: kept as it is
        assertEquals("100% %zz %4", EscapeHelper.unescapeUrl("100% %zz %4"));
        assertEquals("abc", EscapeHelper.unescapeUrl("abc"));
    }

    @Test
    public void testUnescapeIsTheInverseOfEscape() {
        String value = "Tom & Jerry's \"<Show>\" \\ / \n\t caf\u00e9 100% a+b </script>";
        for (EscapeHelper.Kind kind : EscapeHelper.Kind.values()) {
            assertEquals(value, kind.unescape(kind.escape(value)), kind.name());
        }
        assertNull(EscapeHelper.unescape(EscapeHelper.Kind.URL, null));
    }
}
