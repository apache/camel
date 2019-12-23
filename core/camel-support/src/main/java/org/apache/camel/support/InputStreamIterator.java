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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;

/**
 * An {@link InputStream} that wraps an {@link Iterator} which reads iterations as byte array data.
 */
public final class InputStreamIterator extends InputStream {
    private final TypeConverter converter;
    private final Iterator it;
    private InputStream chunk;

    public InputStreamIterator(TypeConverter converter, Iterator it) {
        this.converter = converter;
        this.it = it;
    }

    @Override
    public int read() throws IOException {
        if (chunk == null) {
            chunk = nextChunk();
        }
        if (chunk == null) {
            return -1;
        }
        int data = chunk.read();
        if (data == -1) {
            // initialize for next chunk
            chunk = null;
            return read();
        }

        return data;
    }

    @Override
    public int available() throws IOException {
        if (chunk == null) {
            chunk = nextChunk();
        }
        return chunk != null ? chunk.available() : 0;
    }

    private InputStream nextChunk() throws IOException {
        if (it.hasNext()) {
            try {
                byte[] buf = converter.mandatoryConvertTo(byte[].class, it.next());
                return new ByteArrayInputStream(buf);
            } catch (NoTypeConversionAvailableException e) {
                throw new IOException(e);
            }
        } else {
            return null;
        }
    }
}
