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
import java.io.Serializable;
import java.nio.CharBuffer;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

/**
 * {@link org.apache.camel.StreamCache} implementation for Cache the StreamSource {@link javax.xml.transform.stream.StreamSource}s
 */
public class StreamSourceCache extends StreamSource implements StreamCache {

    private transient InputStream stream;
    private StreamCache streamCache;
    private ReaderCache readCache;

    public StreamSourceCache() {
    }

    public StreamSourceCache(StreamSource source, Exchange exchange) throws IOException {
        if (source.getInputStream() != null) {
            // set up CachedOutputStream with the properties
            CachedOutputStream cos = new CachedOutputStream(exchange.getContext().getProperties());
            IOHelper.copyAndCloseInput(source.getInputStream(), cos);
            streamCache = cos.getStreamCache();
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
        if (stream != null) {
            try {
                stream.reset();
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    @Override
    public InputStream getInputStream() {
        if (stream == null) {
            if (streamCache instanceof InputStream) {
                stream = (InputStream) streamCache;
            } else if (readCache != null) {
                String data = readCache.getData();
                stream = new ByteArrayInputStream(data.getBytes());
            }
        }
        return stream;
    }

    @Override
    public void setInputStream(InputStream inputStream) {
        // noop as the input stream is from stream or reader cache
    }

}