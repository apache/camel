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
package org.apache.camel.converter.stream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.DefaultExchange;


/**
 * Test cases for {@link StreamCacheConverter}
 */
public class StreamCacheConverterTest extends ContextTestSupport {
    
    private static final String TEST_FILE = "org/apache/camel/converter/stream/test.xml";
    private static final String MESSAGE = "<test>This is a test</test>";
    private StreamCacheConverter converter;
    private Exchange exchange;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.converter = new StreamCacheConverter();
        this.exchange = new DefaultExchange(context);
    }
    
    public void testConvertToStreamCache() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(MESSAGE.getBytes());
        StreamCache streamCache = converter.convertToStreamCache(new SAXSource(new InputSource(inputStream)), exchange);
        String message = exchange.getContext().getTypeConverter().convertTo(String.class, streamCache);
        assertNotNull(message);
        assertEquals("The converted message is wrong", MESSAGE, message);
    }

    public void testConvertToStreamCacheStreamSource() throws Exception {
        StreamSource source = new StreamSource(getTestFileStream());
        StreamCache cache = converter.convertToStreamCache(source, exchange);
        //assert re-readability of the cached StreamSource
        XmlConverter converter = new XmlConverter();
        assertNotNull(converter.toString((Source)cache));
        cache.reset();
        assertNotNull(converter.toString((Source)cache));
    }

    public void testConvertToStreamCacheInputStream() throws Exception {
        InputStream is = getTestFileStream();
        InputStream cache = (InputStream)converter.convertToStreamCache(is, exchange);
        //assert re-readability of the cached InputStream
        assertNotNull(IOConverter.toString(cache));
        assertNotNull(IOConverter.toString(cache));
    }
    
    public void testConvertToStreamCacheInpuStreamWithFileCache() throws Exception {
        // set up the properties
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(CachedOutputStream.THRESHOLD, "1");
        exchange.getContext().setProperties(properties);
        InputStream is = getTestFileStream();
        InputStream cache = (InputStream)converter.convertToStreamCache(is, exchange);
        assertNotNull(IOConverter.toString(cache));
        try {
            // since the stream is closed you delete the temp file
            // reset will not work any more
            cache.reset();
            exchange.getUnitOfWork().done(exchange);
            fail("except the exception here");
        } catch (Exception exception) {
            // do nothing
        }
    }


    protected InputStream getTestFileStream() {
        InputStream answer = getClass().getClassLoader().getResourceAsStream(TEST_FILE);
        assertNotNull("Should have found the file: " + TEST_FILE + " on the classpath", answer);
        return answer;
    }
}
