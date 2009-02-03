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
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Converter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A set of {@link Converter} methods for wrapping stream-based messages in a {@link StreamCache}
 * implementation to ensure message re-readability (eg multicasting, retrying)
 */
@Converter
public class StreamCacheConverter {
    private static final transient Log LOG = LogFactory.getLog(StreamCacheConverter.class);

    private XmlConverter converter = new XmlConverter();

    @Converter
    public StreamCache convertToStreamCache(StreamSource source) throws TransformerException {
        return new SourceCache(converter.toString(source));
    }
    
    @Converter
    public StreamCache convertToStreamCache(StringSource source) throws TransformerException {
        //no need to do stream caching for a StringSource
        return null;
    }
    
    @Converter
    public StreamCache convertToStreamCache(BytesSource source) throws TransformerException {
        //no need to do stream caching for a BytesSource
        return null;
    }
    
    @Converter
    public StreamCache convertToStreamCache(SAXSource source) throws TransformerException {
        return new SourceCache(converter.toString(source));
    }

    @Converter
    public StreamCache convertToStreamCache(InputStream stream) throws IOException {
        return new InputStreamCache(IOConverter.toBytes(stream));
    }

    @Converter
    public StreamCache convertToStreamCache(Reader reader) throws IOException {
        return new ReaderCache(IOConverter.toString(reader));
    }

    /*
     * {@link StreamCache} implementation for {@link Source}s
     */
    private class SourceCache extends StringSource implements StreamCache {

        private static final long serialVersionUID = 4147248494104812945L;

        public SourceCache() {
        }

        public SourceCache(String text) {
            super(text);
        }

        public void reset() {
            // do nothing here
        }

    }

    private class InputStreamCache extends ByteArrayInputStream implements StreamCache {

        public InputStreamCache(byte[] data) {
            super(data);
        }

    }

    private class ReaderCache extends StringReader implements StreamCache {

        public ReaderCache(String s) {
            super(s);
        }

        public void reset() {
            try {
                super.reset();
            } catch (IOException e) {
                LOG.warn("Exception is thrown when resets the ReaderCache", e);
            }
        }

        public void close() {
            // Do not release the string for caching
        }

    }


}
