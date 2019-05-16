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
package org.apache.camel.language.xtokenizer;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class XMLTokenExpressionIteratorCharsetTest extends Assert {
    private static final String DATA_TEMPLATE = 
        "<?xml version=\"1.0\" encoding=\"{0}\"?>"
        + "<Statements xmlns=\"http://www.apache.org/xml/test\">"
        + "    <statement>we l\u00f3ve iso-latin</statement>"
        + "    <statement>we h\u00e4te unicode</statement>"
        + "</Statements>";

    private static final String[] RESULTS = {
        "<statement xmlns=\"http://www.apache.org/xml/test\">we l\u00f3ve iso-latin</statement>",
        "<statement xmlns=\"http://www.apache.org/xml/test\">we h\u00e4te unicode</statement>"
    };

    private static final String DATA_STRING = MessageFormat.format(DATA_TEMPLATE, "utf-8");
    private static final byte[] DATA_UTF8 = getBytes(DATA_TEMPLATE, "utf-8");
    private static final byte[] DATA_ISOLATIN = getBytes(DATA_TEMPLATE, "iso-8859-1");

    private static final Map<String, String> NSMAP = Collections.singletonMap("", "http://www.apache.org/xml/test");

    private static byte[] getBytes(String template, String charset) {
        try {
            return MessageFormat.format(template, charset).getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            //ignore
        }
        return null;
    }
    
    @Test
    public void testTokenzeWithUTF8() throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator("//statement", 'i');
        xtei.setNamespaces(NSMAP);

        invokeAndVerify(xtei.createIterator(new ByteArrayInputStream(DATA_UTF8), "utf-8"));
    }

    @Test
    public void testTokenizeWithISOLatin() throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator("//statement", 'i');
        xtei.setNamespaces(NSMAP);

        invokeAndVerify(xtei.createIterator(new ByteArrayInputStream(DATA_ISOLATIN), "iso-8859-1"));
    }

    @Test
    public void testTokenizeWithReader() throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator("//statement", 'i');
        xtei.setNamespaces(NSMAP);

        invokeAndVerify(xtei.createIterator(new StringReader(DATA_STRING)));
    }

    private void invokeAndVerify(Iterator<?> tokenizer) throws IOException, XMLStreamException {
        List<String> results = new ArrayList<>();
        while (tokenizer.hasNext()) {
            String token = (String)tokenizer.next();
            results.add(token);
        }
        ((Closeable)tokenizer).close();
        
        assertEquals("token count", RESULTS.length, results.size());
        for (int i = 0; i < RESULTS.length; i++) {
            assertEquals("mismatch [" + i + "]", RESULTS[i], results.get(i));
        }
        
    }

}
