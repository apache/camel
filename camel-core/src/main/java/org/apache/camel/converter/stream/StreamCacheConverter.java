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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.util.IOHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A set of {@link Converter} methods for wrapping stream-based messages in a {@link StreamCache}
 * implementation to ensure message re-readability (eg multicasting, retrying)
 */
@Converter
public class StreamCacheConverter {
    private static final transient Log LOG = LogFactory.getLog(StreamCacheConverter.class);

    @Converter
    public StreamCache convertToStreamCache(StreamSource source, Exchange exchange) throws IOException {
        return new StreamSourceCache(source, exchange);
    }
    
    @Converter
    public StreamCache convertToStreamCache(StringSource source) {
        //no need to do stream caching for a StringSource
        return null;
    }
    
    @Converter
    public StreamCache convertToStreamCache(BytesSource source) {
        //no need to do stream caching for a BytesSource
        return null;
    }
    
    @Converter
    public StreamCache convertToStreamCache(SAXSource source, Exchange exchange) throws TransformerException {
        String data = exchange.getContext().getTypeConverter().convertTo(String.class, source);
        return new SourceCache(data);
    }

    @Converter
    public StreamCache convertToStreamCache(InputStream stream, Exchange exchange) throws IOException {
        // set up CachedOutputStream with the properties
        CachedOutputStream cos = new CachedOutputStream(exchange.getContext().getProperties());
        IOHelper.copyAndCloseInput(stream, cos);       
        return cos.getStreamCache();
    }

    @Converter
    public StreamCache convertToStreamCache(Reader reader, Exchange exchange) throws IOException {
        String data = exchange.getContext().getTypeConverter().convertTo(String.class, reader);
        return new ReaderCache(data);
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
    
    /*
     * {@link StreamCache} implementation for Cache the StreamSource {@link StreamSource}s
     */
    private class StreamSourceCache extends StreamSource implements StreamCache {
        StreamCache streamCache;
        ReaderCache readCache;
        
        public StreamSourceCache(StreamSource source, Exchange exchange) throws IOException {
            if (source.getInputStream() != null) {
                // set up CachedOutputStream with the properties
                CachedOutputStream cos = new CachedOutputStream(exchange.getContext().getProperties());
                IOHelper.copyAndCloseInput(source.getInputStream(), cos);
                streamCache = cos.getStreamCache();
                setInputStream((InputStream)streamCache);
                setSystemId(source.getSystemId());
            }
            if (source.getReader() != null) {
                String data = exchange.getContext().getTypeConverter().convertTo(String.class, source.getReader());
                readCache = new ReaderCache(data);
                setReader(readCache);
            }
        }

        public void reset() {
            if (streamCache != null) {
                streamCache.reset();
            }
            if (readCache != null) {
                readCache.reset();
            }            
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
                LOG.warn("Cannot reset cache", e);
            }
        }

        public void close() {
            // Do not release the string for caching
        }

    }
    
    


}
