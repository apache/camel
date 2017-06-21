/**
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
package org.apache.camel.converter;

import java.io.InputStream;
import java.io.StringReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import junit.framework.TestCase;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.impl.DefaultFactoryFinderResolver;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class JaxpTest extends TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(JaxpTest.class);
    protected TypeConverter converter = new DefaultTypeConverter(new DefaultPackageScanClassResolver(),
            new ReflectionInjector(), new DefaultFactoryFinderResolver().resolveDefaultFactoryFinder(new DefaultClassResolver()), false);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceHelper.startService(converter);
    }

    public void testConvertToDocument() throws Exception {
        Document document = converter
                .convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        assertNotNull(document);

        LOG.debug("Found document: " + document);

        // lets now convert back again

        String text = converter.convertTo(String.class, document);
        // The preamble changes a little under Java 1.6 it adds a
        // standalone="no" attribute.
        assertTrue("Converted to String: " + text, text.endsWith("<hello>world!</hello>"));
    }

    public void testConvertToSource() throws Exception {
        Source source = converter.convertTo(Source.class, "<hello>world!</hello>");
        assertNotNull(source);

        LOG.debug("Found document: " + source);
    }

    public void testStreamSourceToDomSource() throws Exception {
        StreamSource streamSource = new StreamSource(new StringReader("<hello>world!</hello>"));
        DOMSource domSource = converter.convertTo(DOMSource.class, streamSource);
        assertNotNull("Could not convert to a DOMSource!", domSource);

        LOG.debug("Found document: " + domSource);
    }

    public void testNodeToSourceThenToInputStream() throws Exception {
        Document document = converter.convertTo(Document.class, "<?xml version=\"1.0\"?><hello>world!</hello>");
        Element element = document.getDocumentElement();
        Source source = converter.convertTo(Source.class, element);
        assertNotNull("Could not convert from Node to Source!", source);

        LOG.debug("Found source: " + source);

        InputStream in = converter.convertTo(InputStream.class, source);
        assertNotNull("Could not convert from Source to InputStream!", in);

        String actualText = IOConverter.toString(in, null);
        assertEquals("Text", "<hello>world!</hello>", actualText);

    }
}
