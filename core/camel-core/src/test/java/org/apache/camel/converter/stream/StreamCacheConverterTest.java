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
package org.apache.camel.converter.stream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.xml.StreamSourceConverter;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for {@link StreamCacheConverter}
 */
public class StreamCacheConverterTest extends ContextTestSupport {

    private static final String TEST_FILE = "org/apache/camel/converter/stream/test.xml";
    private static final String MESSAGE = "<test>This is a test</test>";
    private Exchange exchange;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.exchange = new DefaultExchange(context);
    }

    @Test
    public void testConvertToStreamCache() throws Exception {
        context.start();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(MESSAGE.getBytes());
        StreamCache streamCache = StreamSourceConverter.convertToStreamCache(new SAXSource(new InputSource(inputStream)), exchange);
        String message = exchange.getContext().getTypeConverter().convertTo(String.class, streamCache);
        assertNotNull(message);
        assertEquals("The converted message is wrong", MESSAGE, message);
    }

    @Test
    public void testConvertToStreamCacheStreamSource() throws Exception {
        context.start();

        StreamSource source = new StreamSource(getTestFileStream());
        StreamCache cache = StreamSourceConverter.convertToStreamCache(source, exchange);
        // assert re-readability of the cached StreamSource
        XmlConverter converter = new XmlConverter();
        assertNotNull(converter.toString((Source)cache, null));
        cache.reset();
        assertNotNull(converter.toString((Source)cache, null));
    }

    @Test
    public void testConvertToStreamCacheInputStream() throws Exception {
        context.start();

        InputStream is = getTestFileStream();
        InputStream cache = (InputStream)StreamCacheConverter.convertToStreamCache(is, exchange);
        // assert re-readability of the cached InputStream
        String data = IOConverter.toString(cache, null);
        cache.reset();
        String data2 = IOConverter.toString(cache, null);
        assertEquals(data, data2);
    }

    @Test
    public void testConvertToStreamCacheInputStreamWithFileCache() throws Exception {
        exchange.getContext().getStreamCachingStrategy().setSpoolThreshold(1);

        context.start();

        InputStream is = getTestFileStream();
        InputStream cache = (InputStream)StreamCacheConverter.convertToStreamCache(is, exchange);
        assertNotNull(IOConverter.toString(cache, null));
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

    @Test
    public void testConvertToSerializable() throws Exception {
        context.start();

        InputStream is = getTestFileStream();
        StreamCache cache = StreamCacheConverter.convertToStreamCache(is, exchange);
        Serializable ser = StreamSourceConverter.convertToSerializable(cache, exchange);
        assertNotNull(ser);
    }

    @Test
    public void testConvertToByteArray() throws Exception {
        context.start();

        InputStream is = getTestFileStream();
        StreamCache cache = StreamCacheConverter.convertToStreamCache(is, exchange);
        byte[] bytes = StreamCacheConverter.convertToByteArray(cache, exchange);
        assertNotNull(bytes);
    }

    protected InputStream getTestFileStream() {
        InputStream answer = getClass().getClassLoader().getResourceAsStream(TEST_FILE);
        assertNotNull("Should have found the file: " + TEST_FILE + " on the classpath", answer);
        return answer;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
