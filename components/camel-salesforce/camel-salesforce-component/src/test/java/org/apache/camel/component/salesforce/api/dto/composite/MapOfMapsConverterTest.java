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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.xml.XppDomReader;
import com.thoughtworks.xstream.io.xml.xppdom.XppDom;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MapOfMapsConverterTest {

    Converter converter = new MapOfMapsConverter();

    XmlPullParser parser;

    public MapOfMapsConverterTest() throws XmlPullParserException {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        parser = factory.newPullParser();
    }

    @Test
    public void shoulUnmarshallToMapTrivialCase() throws Exception {
        final Object object = converter.unmarshal(readerFor("<holder><some>value</some></holder>"), null);

        assertNotNull(object);
        assertTrue(object instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, String> map = (Map<String, String>)object;

        assertEquals(1, map.size());
        assertEquals("value", map.get("some"));
    }

    @Test
    public void shoulUnmarshallWithAttributesToMapTrivialCase() throws Exception {
        final Object object = converter.unmarshal(readerFor("<holder><some attr=\"attrVal\">value</some></holder>"), null);

        assertNotNull(object);
        assertTrue(object instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = (Map<String, Object>)object;

        assertEquals(1, map.size());
        @SuppressWarnings("unchecked")
        final Map<String, Object> some = (Map<String, Object>)map.get("some");

        assertEquals(2, some.size());

        assertEquals("value", some.get("some"));

        @SuppressWarnings("unchecked")
        final Map<String, String> attributes = (Map<String, String>)some.get("attributes");
        assertEquals(1, attributes.size());
        assertEquals("attrVal", attributes.get("attr"));
    }

    @Test
    public void shoulUnmarshallToMapWithTwoElements() throws Exception {
        final Object object = converter.unmarshal(readerFor("<holder><some1>value1</some1><some2>value2</some2></holder>"), null);

        assertNotNull(object);
        assertTrue(object instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, String> map = (Map<String, String>)object;

        assertEquals(2, map.size());
        assertEquals("value1", map.get("some1"));
        assertEquals("value2", map.get("some2"));
    }

    @Test
    public void shoulUnmarshallToMapWithNestedMap() throws Exception {
        final Object object = converter.unmarshal(readerFor("<holder><some1><some2>value2</some2></some1></holder>"), null);

        assertNotNull(object);
        assertTrue(object instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, String> map = (Map<String, String>)object;

        assertEquals(1, map.size());
        assertEquals(Collections.singletonMap("some2", "value2"), map.get("some1"));
    }

    @Test
    public void shoulUnmarshallToMapWithNestedMapAndAttributes() throws Exception {
        final Object object = converter.unmarshal(readerFor("<holder><some1 attr1=\"val1\"><some2 attr2=\"val2\">value2</some2></some1></holder>"), null);

        assertNotNull(object);
        assertTrue(object instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = (Map<String, Object>)object;

        assertEquals(1, map.size());

        @SuppressWarnings("unchecked")
        final Map<String, Object> some1 = (Map<String, Object>)map.get("some1");

        assertEquals(2, some1.size());

        assertEquals(Collections.singletonMap("attr1", "val1"), some1.get("attributes"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> some2 = (Map<String, Object>)some1.get("some2");
        assertEquals(2, some2.size());

        assertEquals(Collections.singletonMap("attr2", "val2"), some2.get("attributes"));
    }

    HierarchicalStreamReader readerFor(final String xml) throws XmlPullParserException, IOException {
        parser.setInput(new StringReader(xml));
        final XppDom dom = XppDom.build(parser);

        final HierarchicalStreamReader reader = new XppDomReader(dom);
        return reader;
    }
}
