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
package org.apache.camel.util.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.converter.stream.ReaderCache;
import org.apache.camel.util.IOHelper;

/**
 * A {@link org.apache.camel.StreamCache} for {@link javax.xml.transform.stream.StreamSource}s
 */
public final class StreamSourceCache extends StreamSource implements StreamCache {

    private final StreamCache streamCache;
    private final ReaderCache readCache;

    public StreamSourceCache(StreamSource source, Exchange exchange) throws IOException {
        if (source.getInputStream() != null) {
            // set up CachedOutputStream with the properties
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copyAndCloseInput(source.getInputStream(), cos);
            streamCache = cos.newStreamCache();
            readCache = null;
            setSystemId(source.getSystemId());
            setInputStream((InputStream) streamCache);
        } else if (source.getReader() != null) {
            String data = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, source.getReader());
            readCache = new ReaderCache(data);
            streamCache = null;
            setReader(readCache);
        } else {
            streamCache = null;
            readCache = null;
        }
    }
    
    public StreamSourceCache(StreamCache streamCache) {
        this.streamCache = streamCache;
        if (streamCache instanceof InputStream) {
            setInputStream((InputStream) streamCache);
            this.readCache = null;
        } else if (streamCache instanceof ReaderCache) {
            this.readCache = (ReaderCache) streamCache;
            setReader((java.io.Reader) streamCache);
        } else {
            this.readCache = null;
        }
    }
    
    @Override
    public void reset() {
        if (streamCache != null) {
            streamCache.reset();
        }
        if (readCache != null) {
            readCache.reset();
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        if (streamCache != null) {
            streamCache.writeTo(os);
        } else if (readCache != null) {
            readCache.writeTo(os);
        }
    }

    @Override
    public StreamCache copy(Exchange exchange) throws IOException {
        if (streamCache != null) {
            return new StreamSourceCache(streamCache.copy(exchange));
        }
        if (readCache != null) {
            return new StreamSourceCache(readCache.copy(exchange));
        }
        return null;
    }

    @Override
    public boolean inMemory() {
        if (streamCache != null) {
            return streamCache.inMemory();
        } else if (readCache != null) {
            return readCache.inMemory();
        } else {
            // should not happen
            return true;
        }
    }

    
    @Override
    public long length() {
        if (streamCache != null) {
            return streamCache.length();
        } else if (readCache != null) {
            return readCache.length();
        } else {
            // should not happen
            return 0;
        }
    }

}
