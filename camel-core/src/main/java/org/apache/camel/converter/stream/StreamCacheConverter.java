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

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Converter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;

/**
 * A set of {@link Converter} methods for wrapping stream-based messages in a {@link StreamCache}
 * implementation to ensure message re-readability (eg multicasting, retrying)
 */
@Converter
public class StreamCacheConverter {
    
    private XmlConverter converter = new XmlConverter();

    @Converter
    public StreamCache convertToStreamCache(StreamSource source) throws TransformerException {
        //TODO: we can probably build a more generic converter method to support other kinds of Sources as well (e.g. SAXSource, StAXSource, ...)
        return new StreamSourceCache(converter.toString(source));
    }
    
    @Converter
    public StreamCache convertToStreamCache(InputStream stream) throws IOException {
        return new InputStreamCache(IOConverter.toBytes(stream));
    }

    private class StreamSourceCache extends StringSource implements StreamCache {
        
        private static final long serialVersionUID = 4147248494104812945L;

        public StreamSourceCache(String text) {
            super(text);
        }
    }
    
    private class InputStreamCache extends ByteArrayInputStream implements StreamCache {
     
        public InputStreamCache(byte[] data) {
            super(data);
        }
        
    }
}
